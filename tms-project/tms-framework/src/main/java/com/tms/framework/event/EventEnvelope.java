package com.tms.framework.event;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import tools.jackson.databind.JsonNode;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 事实类消息的统一信封（[06-api-contracts] §3）。
 *
 * <p>为什么用 record 而不是可写 POJO：信封一旦发出就不该被改动。发送方改字段，
 * 接收方看到的和落库的就是两个东西——这是最难查的一类问题。
 *
 * <p>构造即校验：缺字段的信封在<b>产生它的那一刻</b>就失败，而不是等到接收方 400。
 * 接收方反序列化出非法信封同样会抛 {@link IllegalArgumentException}，
 * 由接收端点映射成契约错误（4xx），不重试。
 *
 * @param lineNo           单头级事件填 0（[06] §3 标注为 `[待定]`，暂按 0 处理）
 * @param aggregateVersion 同一单行的事实版本号，接收方据此丢弃旧版本（[ADR-013] 状态只前进）
 * @param payload          事件明细，结构见 [06] §4
 * @param traceId          可选链路追踪；未接入链路追踪前为 null
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record EventEnvelope(

        @JsonProperty("event_id") String eventId,
        @JsonProperty("event_type") String eventType,
        @JsonProperty("occurred_at") LocalDateTime occurredAt,
        @JsonProperty("source_system") String sourceSystem,
        @JsonProperty("biz_order_no") String bizOrderNo,
        @JsonProperty("line_no") Integer lineNo,
        @JsonProperty("aggregate_version") Integer aggregateVersion,
        @JsonProperty("payload") JsonNode payload,
        @JsonProperty("trace_id") String traceId) {

    /** 本系统在信封里对外标识（[06] §3 source_system 取值之一）。 */
    public static final String SOURCE_TMS = "TMS";

    public EventEnvelope {
        List<String> missing = new ArrayList<>();
        if (isBlank(eventId)) {
            missing.add("event_id");
        }
        if (isBlank(eventType)) {
            missing.add("event_type");
        }
        if (occurredAt == null) {
            missing.add("occurred_at");
        }
        if (isBlank(sourceSystem)) {
            missing.add("source_system");
        }
        if (isBlank(bizOrderNo)) {
            missing.add("biz_order_no");
        }
        if (lineNo == null) {
            missing.add("line_no");
        }
        if (aggregateVersion == null) {
            missing.add("aggregate_version");
        }
        if (payload == null || payload.isNull()) {
            missing.add("payload");
        }
        if (!missing.isEmpty()) {
            throw new IllegalArgumentException("事件信封缺少必填字段: " + String.join(", ", missing));
        }
        if (lineNo < 0) {
            throw new IllegalArgumentException("line_no 不允许为负: " + lineNo);
        }
        if (aggregateVersion < 1) {
            throw new IllegalArgumentException("aggregate_version 从 1 起算: " + aggregateVersion);
        }
    }

    /**
     * 幂等去重键（[06] §6-1）：{@code event_id + biz_order_no + line_no + event_type}。
     *
     * <p>刻意把 {@code event_id} 也放进去——{@code event_id} 唯一时其余三项本就唯一，
     * 看着冗余，但这样索引前缀能被"同一单据的事件"这类排障查询直接复用。
     */
    public String dedupKey() {
        return eventId + "|" + bizOrderNo + "|" + lineNo + "|" + eventType;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}

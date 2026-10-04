package com.tms.framework.event;

import com.tms.framework.event.entity.OutboxEvent;
import com.tms.framework.event.mapper.OutboxEventMapper;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 发件箱写入：<b>业务事务内</b>落一条待投递事件，由 {@code OutboxDispatcher} 异步发送。
 *
 * <p>本类刻意<b>不加</b> {@code @Transactional}：它必须加入调用方的业务事务，
 * 拿自己的新事务就退化成了"异步发消息"，恰好丢掉事务性发件箱要保证的那件事。
 */
@Component
public class OutboxRecorder {

    private final OutboxEventMapper outboxEventMapper;
    private final ObjectMapper objectMapper;

    public OutboxRecorder(OutboxEventMapper outboxEventMapper, ObjectMapper objectMapper) {
        this.outboxEventMapper = outboxEventMapper;
        this.objectMapper = objectMapper;
    }

    /**
     * 记录一条待投递事件。
     *
     * @param targetSystem 目标系统，与订阅方的 {@link EventPublisher#targetSystem()} 对应
     * @return 已落库的行（含生成的 ID 与初始状态），供调用方断言/日志使用
     */
    public OutboxEvent record(EventEnvelope envelope, String targetSystem) {
        TransactionGuard.requireActiveTransaction("OutboxRecorder.record");
        if (targetSystem == null || targetSystem.isBlank()) {
            throw new IllegalArgumentException("targetSystem 不能为空：事件没有收件方就无法投递");
        }
        String payload = serializePayload(envelope);

        OutboxEvent event = new OutboxEvent();
        event.setEventId(envelope.eventId());
        event.setEventType(envelope.eventType());
        event.setSourceSystem(envelope.sourceSystem());
        event.setBizOrderNo(envelope.bizOrderNo());
        event.setLineNo(envelope.lineNo());
        event.setAggregateVersion(envelope.aggregateVersion());
        event.setOccurredAt(envelope.occurredAt());
        event.setPayload(payload);
        event.setTargetSystem(targetSystem);
        event.setTraceId(envelope.traceId());
        event.setStatus(OutboxStatus.PENDING.name());
        event.setRetryCount(0);
        event.setNextRetryAt(null);

        outboxEventMapper.insert(event);
        return event;
    }

    /**
     * 构造并记录一条 TMS 自身产生的事实事件——自动生成 {@code event_id}、
     * 取当前时间为 {@code occurred_at}、{@code source_system} 固定为 TMS。
     *
     * <p>{@code event_id} 在此处生成一次即固定：投递重试复用同一 {@code event_id}，
     * 接收方靠它幂等（[06] §3）。若改成每次重试新生成，接收方会把重试当新事件，重复执行。
     */
    public OutboxEvent recordFact(String eventType,
                                  String bizOrderNo,
                                  int lineNo,
                                  int aggregateVersion,
                                  Object payload,
                                  String targetSystem) {
        EventEnvelope envelope = new EventEnvelope(
                UUID.randomUUID().toString(),
                eventType,
                LocalDateTime.now(),
                EventEnvelope.SOURCE_TMS,
                bizOrderNo,
                lineNo,
                aggregateVersion,
                objectMapper.valueToTree(payload),
                null);
        return record(envelope, targetSystem);
    }

    private String serializePayload(EventEnvelope envelope) {
        try {
            return objectMapper.writeValueAsString(envelope.payload());
        } catch (JacksonException ex) {
            throw new IllegalStateException(
                    "事件负载无法序列化 event_id=" + envelope.eventId() + " type=" + envelope.eventType(), ex);
        }
    }
}

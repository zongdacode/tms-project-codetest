package com.tms.framework.event.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 发件箱（[05-data-model] §3.1）：业务事务内同事务落库，投递器异步扫描发送——
 * 事务性发件箱模式，保证"业务成功 ⇔ 事件必达（至少一次）"。
 *
 * <p><b>刻意不继承 BaseEntity</b>：这张表是投递基础设施，不是业务实体。
 * 没有 {@code deleted}（发出去的事件不能"删掉"，只能置 DEAD 等人工处理），
 * 也没有 {@code version}（并发控制靠状态+重试次数的条件更新，不靠乐观锁）。
 *
 * <p><b>相对 [05-data-model] §3.1 的补列</b>：该表原定义只有
 * {@code event_id/event_type/biz_order_no/line_no/occurred_at/payload/target_system/status/retry_count/next_retry_at}，
 * 缺 {@code source_system}、{@code aggregate_version}、{@code trace_id}——这三项从其余列推不出来，
 * 导致投递时无法还原 [06] §3 要求的完整信封。此处补上，文档同步已更新。
 *
 * <p><b>为什么 payload 只存事件明细、不存整个信封</b>：信封的关键字段已经是独立列，
 * 可被 {@code (status, next_retry_at)} 索引和排障 SQL 直接使用；再存一份完整信封会有
 * 两份真相，一旦不一致，无法判断哪份是发出的那份。
 */
@TableName("outbox_event")
public class OutboxEvent implements Serializable {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    @TableField("event_id")
    private String eventId;

    @TableField("event_type")
    private String eventType;

    @TableField("source_system")
    private String sourceSystem;

    @TableField("biz_order_no")
    private String bizOrderNo;

    @TableField("line_no")
    private Integer lineNo;

    @TableField("aggregate_version")
    private Integer aggregateVersion;

    @TableField("occurred_at")
    private LocalDateTime occurredAt;

    @TableField("payload")
    private String payload;

    @TableField("target_system")
    private String targetSystem;

    @TableField("trace_id")
    private String traceId;

    @TableField("status")
    private String status;

    @TableField("retry_count")
    private Integer retryCount;

    @TableField("next_retry_at")
    private LocalDateTime nextRetryAt;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField("sent_at")
    private LocalDateTime sentAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getEventId() {
        return eventId;
    }

    public void setEventId(String eventId) {
        this.eventId = eventId;
    }

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public String getSourceSystem() {
        return sourceSystem;
    }

    public void setSourceSystem(String sourceSystem) {
        this.sourceSystem = sourceSystem;
    }

    public String getBizOrderNo() {
        return bizOrderNo;
    }

    public void setBizOrderNo(String bizOrderNo) {
        this.bizOrderNo = bizOrderNo;
    }

    public Integer getLineNo() {
        return lineNo;
    }

    public void setLineNo(Integer lineNo) {
        this.lineNo = lineNo;
    }

    public Integer getAggregateVersion() {
        return aggregateVersion;
    }

    public void setAggregateVersion(Integer aggregateVersion) {
        this.aggregateVersion = aggregateVersion;
    }

    public LocalDateTime getOccurredAt() {
        return occurredAt;
    }

    public void setOccurredAt(LocalDateTime occurredAt) {
        this.occurredAt = occurredAt;
    }

    public String getPayload() {
        return payload;
    }

    public void setPayload(String payload) {
        this.payload = payload;
    }

    public String getTargetSystem() {
        return targetSystem;
    }

    public void setTargetSystem(String targetSystem) {
        this.targetSystem = targetSystem;
    }

    public String getTraceId() {
        return traceId;
    }

    public void setTraceId(String traceId) {
        this.traceId = traceId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Integer getRetryCount() {
        return retryCount;
    }

    public void setRetryCount(Integer retryCount) {
        this.retryCount = retryCount;
    }

    public LocalDateTime getNextRetryAt() {
        return nextRetryAt;
    }

    public void setNextRetryAt(LocalDateTime nextRetryAt) {
        this.nextRetryAt = nextRetryAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getSentAt() {
        return sentAt;
    }

    public void setSentAt(LocalDateTime sentAt) {
        this.sentAt = sentAt;
    }
}

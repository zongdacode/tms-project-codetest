package com.example.gate0.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * 发件箱（[ADR-011]）最小模型：验证「业务事务内同事务落 outbox」在 Boot 4 + MP 3.5.17 下可行。
 */
@TableName("outbox_event")
public class OutboxEvent {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    @TableField("event_id")
    private String eventId;

    @TableField("event_type")
    private String eventType;

    @TableField("biz_order_no")
    private String bizOrderNo;

    @TableField("line_no")
    private Integer lineNo;

    @TableField("payload")
    private String payload;

    @TableField("status")
    private String status;

    @TableField("retry_count")
    private Integer retryCount;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getEventId() { return eventId; }
    public void setEventId(String eventId) { this.eventId = eventId; }

    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }

    public String getBizOrderNo() { return bizOrderNo; }
    public void setBizOrderNo(String bizOrderNo) { this.bizOrderNo = bizOrderNo; }

    public Integer getLineNo() { return lineNo; }
    public void setLineNo(Integer lineNo) { this.lineNo = lineNo; }

    public String getPayload() { return payload; }
    public void setPayload(String payload) { this.payload = payload; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Integer getRetryCount() { return retryCount; }
    public void setRetryCount(Integer retryCount) { this.retryCount = retryCount; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}

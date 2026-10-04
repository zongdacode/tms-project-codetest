package com.example.gate0.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/** 收件箱幂等记录（[05-data-model] §3.2）。 */
@TableName("inbox_consumed")
public class InboxConsumed {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    @TableField("event_id")
    private String eventId;

    @TableField("biz_order_no")
    private String bizOrderNo;

    @TableField("line_no")
    private Integer lineNo;

    @TableField("event_type")
    private String eventType;

    @TableField("result_summary")
    private String resultSummary;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getEventId() { return eventId; }
    public void setEventId(String eventId) { this.eventId = eventId; }

    public String getBizOrderNo() { return bizOrderNo; }
    public void setBizOrderNo(String bizOrderNo) { this.bizOrderNo = bizOrderNo; }

    public Integer getLineNo() { return lineNo; }
    public void setLineNo(Integer lineNo) { this.lineNo = lineNo; }

    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }

    public String getResultSummary() { return resultSummary; }
    public void setResultSummary(String resultSummary) { this.resultSummary = resultSummary; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}

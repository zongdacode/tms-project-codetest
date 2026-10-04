package com.tms.framework.event.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 收件箱幂等记录（[05-data-model] §3.2）。
 *
 * <p>唯一键 {@code uk_consume (event_id, biz_order_no, line_no, event_type)} 是幂等的<b>全部依据</b>——
 * 并发重复投递靠 DB 唯一约束兜住，不靠"先查再插"（那中间有窗口）。
 *
 * <p>本表不继承 BaseEntity：它是消费凭证，不是业务实体；没有逻辑删除
 * （删掉幂等记录 = 允许重复执行，语义上就是错的）。
 */
@TableName("inbox_consumed")
public class InboxConsumed implements Serializable {

    /** 消费成功但业务尚未回填摘要时的占位值。 */
    public static final String PENDING_SUMMARY = "";

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

    @TableField("consumed_at")
    private LocalDateTime consumedAt;

    @TableField("result_summary")
    private String resultSummary;

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

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public LocalDateTime getConsumedAt() {
        return consumedAt;
    }

    public void setConsumedAt(LocalDateTime consumedAt) {
        this.consumedAt = consumedAt;
    }

    public String getResultSummary() {
        return resultSummary;
    }

    public void setResultSummary(String resultSummary) {
        this.resultSummary = resultSummary;
    }
}

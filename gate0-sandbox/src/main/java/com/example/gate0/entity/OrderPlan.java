package com.example.gate0.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;

import java.time.LocalDateTime;

/**
 * 计划表最小模型（[05-data-model] §2），只取验证需要的列，用于验证：
 * 乐观锁 @Version、审计自动填充、唯一键 uk_plan(biz_order_no, line_no)。
 */
@TableName("tms_order_plan")
public class OrderPlan {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    @TableField("biz_order_no")
    private String bizOrderNo;

    @TableField("line_no")
    private Integer lineNo;

    @TableField("source_system")
    private String sourceSystem;

    @TableField("source_doc_no")
    private String sourceDocNo;

    @TableField("outbound_status")
    private Integer outboundStatus;

    @TableField("signed_status")
    private Integer signedStatus;

    @TableField("cancel_flag")
    private Integer cancelFlag;

    @Version
    @TableField("version")
    private Integer version;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getBizOrderNo() { return bizOrderNo; }
    public void setBizOrderNo(String bizOrderNo) { this.bizOrderNo = bizOrderNo; }

    public Integer getLineNo() { return lineNo; }
    public void setLineNo(Integer lineNo) { this.lineNo = lineNo; }

    public String getSourceSystem() { return sourceSystem; }
    public void setSourceSystem(String sourceSystem) { this.sourceSystem = sourceSystem; }

    public String getSourceDocNo() { return sourceDocNo; }
    public void setSourceDocNo(String sourceDocNo) { this.sourceDocNo = sourceDocNo; }

    public Integer getOutboundStatus() { return outboundStatus; }
    public void setOutboundStatus(Integer outboundStatus) { this.outboundStatus = outboundStatus; }

    public Integer getSignedStatus() { return signedStatus; }
    public void setSignedStatus(Integer signedStatus) { this.signedStatus = signedStatus; }

    public Integer getCancelFlag() { return cancelFlag; }
    public void setCancelFlag(Integer cancelFlag) { this.cancelFlag = cancelFlag; }

    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}

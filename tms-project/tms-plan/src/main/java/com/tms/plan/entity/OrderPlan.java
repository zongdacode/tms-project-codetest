package com.tms.plan.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 计划表（[05-data-model] §2）。粒度：业务单号 + 行号 = 一行。
 *
 * <p><b>为什么不继承 BaseEntity</b>：红线的可执行性是靠"字段白名单 == 映射列"来保证的
 * （[10] R-2，架构测试 R2PlanTableFieldWhitelistTest）。BaseEntity 带 {@code created_by}、
 * {@code deleted}、{@code tenant_id} 三列，而 [05-data-model] §2 的字段清单里没有它们——
 * 一旦继承，白名单立刻与实体不符，要么测试被迫放水，要么多出三列没人认领。
 * 这里选择让实体严格等于文档字段清单，红线才有意义。
 *
 * <p><b>红线约束</b>：
 * <ul>
 *   <li>R-1：本模块与 tms-core 不得互相依赖 Mapper/Entity，联动只走 {@code api/} 接口；</li>
 *   <li>R-2：只存四节点状态（下发/出库完成/入库完成/签收完成），
 *       <b>禁止</b>新增仓内作业、运输执行细节字段。加字段前先读 [10] §5 的变更流程。</li>
 * </ul>
 *
 * <p>整体迁出为订单中心的锚点（[ADR-015]、[09]），迁移路径依赖"本实体与运单域零耦合"。
 */
@TableName("tms_order_plan")
public class OrderPlan implements Serializable {

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

    @TableField("order_type")
    private String orderType;

    @TableField("ship_from_code")
    private String shipFromCode;

    @TableField("ship_to_code")
    private String shipToCode;

    @TableField("plan_qty")
    private BigDecimal planQty;

    /** 下发：0 未 / 1 已（建档即 1）。 */
    @TableField("dispatch_status")
    private Integer dispatchStatus;

    @TableField("dispatched_at")
    private LocalDateTime dispatchedAt;

    /** 出库完成：0 / 1。 */
    @TableField("outbound_status")
    private Integer outboundStatus;

    @TableField("outbound_completed_at")
    private LocalDateTime outboundCompletedAt;

    /** 入库完成：0 / 1。 */
    @TableField("inbound_status")
    private Integer inboundStatus;

    @TableField("inbound_completed_at")
    private LocalDateTime inboundCompletedAt;

    /** 签收完成：0 / 1。 */
    @TableField("signed_status")
    private Integer signedStatus;

    @TableField("signed_completed_at")
    private LocalDateTime signedCompletedAt;

    /** 取消标记：独立列，不改节点状态（[ADR-013] 状态只前进）。 */
    @TableField("cancel_flag")
    private Integer cancelFlag;

    @TableField("cancelled_at")
    private LocalDateTime cancelledAt;

    @TableField("cancel_reason")
    private String cancelReason;

    /** 冲销标记：独立列，不改节点状态。 */
    @TableField("reverse_flag")
    private Integer reverseFlag;

    @TableField("reversed_at")
    private LocalDateTime reversedAt;

    /** 最近消费的事件 ID，仅用于排障，不参与业务判断。 */
    @TableField("last_event_id")
    private String lastEventId;

    /** 乐观锁；{@code fill = INSERT} 的理由见 {@code BaseEntity#version}。 */
    @Version
    @TableField(value = "version", fill = FieldFill.INSERT)
    private Integer version;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
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

    public String getSourceSystem() {
        return sourceSystem;
    }

    public void setSourceSystem(String sourceSystem) {
        this.sourceSystem = sourceSystem;
    }

    public String getSourceDocNo() {
        return sourceDocNo;
    }

    public void setSourceDocNo(String sourceDocNo) {
        this.sourceDocNo = sourceDocNo;
    }

    public String getOrderType() {
        return orderType;
    }

    public void setOrderType(String orderType) {
        this.orderType = orderType;
    }

    public String getShipFromCode() {
        return shipFromCode;
    }

    public void setShipFromCode(String shipFromCode) {
        this.shipFromCode = shipFromCode;
    }

    public String getShipToCode() {
        return shipToCode;
    }

    public void setShipToCode(String shipToCode) {
        this.shipToCode = shipToCode;
    }

    public BigDecimal getPlanQty() {
        return planQty;
    }

    public void setPlanQty(BigDecimal planQty) {
        this.planQty = planQty;
    }

    public Integer getDispatchStatus() {
        return dispatchStatus;
    }

    public void setDispatchStatus(Integer dispatchStatus) {
        this.dispatchStatus = dispatchStatus;
    }

    public LocalDateTime getDispatchedAt() {
        return dispatchedAt;
    }

    public void setDispatchedAt(LocalDateTime dispatchedAt) {
        this.dispatchedAt = dispatchedAt;
    }

    public Integer getOutboundStatus() {
        return outboundStatus;
    }

    public void setOutboundStatus(Integer outboundStatus) {
        this.outboundStatus = outboundStatus;
    }

    public LocalDateTime getOutboundCompletedAt() {
        return outboundCompletedAt;
    }

    public void setOutboundCompletedAt(LocalDateTime outboundCompletedAt) {
        this.outboundCompletedAt = outboundCompletedAt;
    }

    public Integer getInboundStatus() {
        return inboundStatus;
    }

    public void setInboundStatus(Integer inboundStatus) {
        this.inboundStatus = inboundStatus;
    }

    public LocalDateTime getInboundCompletedAt() {
        return inboundCompletedAt;
    }

    public void setInboundCompletedAt(LocalDateTime inboundCompletedAt) {
        this.inboundCompletedAt = inboundCompletedAt;
    }

    public Integer getSignedStatus() {
        return signedStatus;
    }

    public void setSignedStatus(Integer signedStatus) {
        this.signedStatus = signedStatus;
    }

    public LocalDateTime getSignedCompletedAt() {
        return signedCompletedAt;
    }

    public void setSignedCompletedAt(LocalDateTime signedCompletedAt) {
        this.signedCompletedAt = signedCompletedAt;
    }

    public Integer getCancelFlag() {
        return cancelFlag;
    }

    public void setCancelFlag(Integer cancelFlag) {
        this.cancelFlag = cancelFlag;
    }

    public LocalDateTime getCancelledAt() {
        return cancelledAt;
    }

    public void setCancelledAt(LocalDateTime cancelledAt) {
        this.cancelledAt = cancelledAt;
    }

    public String getCancelReason() {
        return cancelReason;
    }

    public void setCancelReason(String cancelReason) {
        this.cancelReason = cancelReason;
    }

    public Integer getReverseFlag() {
        return reverseFlag;
    }

    public void setReverseFlag(Integer reverseFlag) {
        this.reverseFlag = reverseFlag;
    }

    public LocalDateTime getReversedAt() {
        return reversedAt;
    }

    public void setReversedAt(LocalDateTime reversedAt) {
        this.reversedAt = reversedAt;
    }

    public String getLastEventId() {
        return lastEventId;
    }

    public void setLastEventId(String lastEventId) {
        this.lastEventId = lastEventId;
    }

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}

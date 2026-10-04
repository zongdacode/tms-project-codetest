package com.tms.plan.api;

import java.io.Serializable;
import java.util.Objects;

/**
 * 计划行关联键：业务单号 + 行号。
 *
 * <p>这是全系统唯一的跨系统关联键（[ADR-012]）：biz_order_no 由 TMS 统一下发并回传上游，
 * line_no 单内自增、变更拆行只追加不复用。把它做成一个类型而不是到处传两个散参数，
 * 是为了让"关联键是什么"只有一处定义——散参数最容易在某处被漏掉或写反顺序。
 *
 * <p>位于 {@code api/} 包：其他模块引用计划行时必须用本类型，
 * 不允许 import {@code com.tms.plan.entity.OrderPlan}（红线 [10] R-1，架构测试会拦）。
 *
 * @param lineNo 单头级事件的关联用 0（[06] §3 该规则标注为 `[待定]`）
 */
public record PlanLineKey(String bizOrderNo, Integer lineNo) implements Serializable {

    public PlanLineKey {
        if (bizOrderNo == null || bizOrderNo.isBlank()) {
            throw new IllegalArgumentException("bizOrderNo 不能为空");
        }
        if (lineNo == null || lineNo < 0) {
            throw new IllegalArgumentException("lineNo 不能为空且不允许为负: " + lineNo);
        }
    }

    @Override
    public String toString() {
        return bizOrderNo + "#" + lineNo;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        return other instanceof PlanLineKey that
                && lineNo.equals(that.lineNo)
                && bizOrderNo.equals(that.bizOrderNo);
    }

    @Override
    public int hashCode() {
        return Objects.hash(bizOrderNo, lineNo);
    }
}

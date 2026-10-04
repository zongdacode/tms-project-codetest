package com.tms.core.mapper;

import com.tms.plan.entity.OrderPlan;

/**
 * <b>故意违规的样例，不要"修好"它。</b>
 *
 * <p>它模拟的是最常见的红线破坏方式：写运单域代码时，图省事直接注入计划表的实体，
 * 而不是走 {@code com.tms.plan.api}。代价是运单域与计划表绑死，
 * 计划表模块再也搬不到订单中心去（[ADR-015]、[09-migration]）。
 *
 * <p>它的唯一用途是被 {@code ArchRulesCounterexampleTest} 导入，
 * 断言"R-1 规则确实会拦下这种代码"。如果哪天有人把这个类改干净了，
 * 那条断言会失败——那时要修的是<b>断言</b>背后的样例，不是把规则删掉。
 *
 * <p>位于 test sources，且导入真实代码做规则检查时用
 * {@code DO_NOT_INCLUDE_TESTS} 排除，所以它不会影响"真实代码是否合规"的结论。
 */
public class PlanEntityTouchingFixture {

    /** 违规点：持有计划表实体。 */
    private OrderPlan orderPlan;

    public OrderPlan currentPlan() {
        return orderPlan;
    }
}

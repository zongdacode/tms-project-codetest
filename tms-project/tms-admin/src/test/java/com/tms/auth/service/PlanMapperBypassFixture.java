package com.tms.auth.service;

import com.tms.plan.mapper.OrderPlanMapper;

/**
 * <b>故意违规的样例，不要"修好"它。</b>
 *
 * <p>违反的是 A-3（跨模块只能依赖对方 {@code api/} 包）：这里跨了两个模块，
 * 还直接拿到了对方的 Mapper——比引用实体更严重，连"绕过服务层直接查表"都做到了
 * （[05-系统架构] §3 第 1、3 条）。
 *
 * <p>被 {@code ArchRulesCounterexampleTest} 用来证明 A-3 规则真的会报错。
 */
public class PlanMapperBypassFixture {

    /** 违规点：跨模块注入对方 Mapper。 */
    private OrderPlanMapper orderPlanMapper;

    public OrderPlanMapper mapper() {
        return orderPlanMapper;
    }
}

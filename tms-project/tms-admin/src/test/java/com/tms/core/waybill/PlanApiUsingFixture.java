package com.tms.core.waybill;

import com.tms.plan.api.PlanLineKey;

/**
 * <b>合规样例</b>：运单域通过 {@code com.tms.plan.api.PlanLineKey} 引用计划行。
 *
 * <p>它的作用不是"再多测一个类"，而是证明规则<b>不是无差别拦截</b>：
 * 一个把跨模块依赖一律禁掉的规则也能让违规样例失败，但那不是我们要的规则。
 * 只有"违规的失败、合规的通过"同时成立，规则才叫可用。
 *
 * <p>它依赖的 {@link PlanLineKey} 也是真实契约类型（[ADR-012] 关联键），不是为测试造的。
 */
public class PlanApiUsingFixture {

    private PlanLineKey planLineKey;

    public PlanLineKey key() {
        return planLineKey;
    }
}

package com.tms.arch;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tms.auth.service.PlanMapperBypassFixture;
import com.tms.plan.api.PlanLineKey;
import com.tms.plan.entity.OrderPlan;
import com.tms.core.mapper.PlanEntityTouchingFixture;
import com.tms.core.waybill.PlanApiUsingFixture;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 证明红线规则<b>真的在检查</b>，而不只是"没报错"。
 *
 * <p>架构测试最常见的问题不是漏写规则，而是规则写错却永远通过：
 * 包名多打一个点、{@code Predicate} 写反、导入范围不对——表现都是绿灯。
 * 唯一的发现方式是拿一个确定违规的样例去撞它。
 *
 * <p>第二个必要条件是反向验证：合规样例必须通过。只会一律报错的规则同样没用。
 *
 * <p><b>为什么有些用例要额外导入 {@link OrderPlan} / {@link PlanLineKey}</b>：
 * {@code planAndExecutionMustNotShareInternals()} 是两条规则的合取，
 * 两条的 {@code that()} 各自匹配"运单域"和"计划表"两侧。若只导入一侧，
 * 另一侧"没有任何类可检查"——ArchUnit 1.x 默认
 * {@code failOnEmptyShould = true}，这种情况会直接报错而不是当成功。
 * 这个默认值是有意的：一条匹配不到任何类的规则，等于没有规则。
 * 新写规则时若看到 "failed to check any classes"，先怀疑导入范围，别急着关掉这个开关。
 */
class ArchRulesCounterexampleTest {

    private static JavaClasses only(Class<?>... classes) {
        return new ClassFileImporter().importClasses(classes);
    }

    @Test
    @DisplayName("R-1 规则必须拦下「运单域直接引用计划表实体」")
    void r1RuleMustCatchExecutionModuleTouchingPlanEntity() {
        // 同时导入计划表侧的真实类，让规则的另一半也有类可查（见类注释）
        JavaClasses violating = only(PlanEntityTouchingFixture.class, OrderPlan.class);

        assertThatThrownBy(() -> ArchRules.planAndExecutionMustNotShareInternals().check(violating))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("com.tms.plan.entity.OrderPlan");
    }

    @Test
    @DisplayName("A-3 规则必须拦下「跨模块直接注入对方 Mapper」")
    void a3RuleMustCatchCrossModuleMapperInjection() {
        JavaClasses violating = only(PlanMapperBypassFixture.class);

        assertThatThrownBy(() -> ArchRules.moduleMustOnlyUseOtherModuleApi("auth").check(violating))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("com.tms.plan.mapper.OrderPlanMapper");
    }

    @Test
    @DisplayName("合规样例「通过 api/ 包引用计划行」必须放行——规则不能一律报错")
    void apiPackageUsageMustBeAllowed() {
        JavaClasses compliant = only(PlanApiUsingFixture.class, PlanLineKey.class);

        assertThatCode(() -> ArchRules.moduleMustOnlyUseOtherModuleApi("core").check(compliant))
                .doesNotThrowAnyException();
        assertThatCode(() -> ArchRules.planAndExecutionMustNotShareInternals().check(compliant))
                .doesNotThrowAnyException();
    }
}

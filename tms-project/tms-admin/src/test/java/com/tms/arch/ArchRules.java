package com.tms.arch;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.CompositeArchRule;

import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * 红线规则的<b>唯一定义处</b>。
 *
 * <p>规则单独抽出来是为了让"故意违规的样例"能验证规则本身有效
 * （见 {@code ArchRulesCounterexampleTest}）。规则和它的验证写在一起、只对真实代码跑一次，
 * 就无法区分"代码没问题"和"规则根本没在检查"——后者更常见，也更难发现。
 *
 * <p><b>2026-10-05 收拢</b>（[ADR-018]）：全系统只剩 {@code tms-plan} ↔ {@code tms-core}
 * 这一条模块边界。原先的 A-3（"每个业务模块都开 api/ 子包、跨模块只走 api/"）已删除——
 * auth/base 并入 tms-core 后，A-3 与下面的 R-1 语义完全重合，留着只是同一件事检查两遍。
 *
 * <p>这几条只覆盖<b>代码结构</b>层面的红线。SQL 里的跨表 join（[10] R-1 的后半句）
 * 靠字符串扫描，ArchUnit 看不见，别以为过了这些测试就等于 R-1 全守住。
 */
public final class ArchRules {

    private ArchRules() {
    }

    /**
     * [10] R-1：计划表与执行域不得互相依赖内部模型。
     *
     * <p>{@code com.tms.core..} 覆盖整个业务模块——含已并入的 {@code auth} / {@code base} 域。
     * 它们与 {@code shipment/waybill/...} 一样受这条红线约束：谁都不许碰 {@code OrderPlan}。
     *
     * <p>只允许通过对方的 {@code api/} 包交互——这是 [09-migration] 里"计划表模块整体搬去订单中心"
     * 的前提。一旦运单域直接读了 {@code OrderPlan} 实体，迁移时就要连着改运单代码，
     * 迁移窗口从"换实现"变成"改业务"。
     */
    public static ArchRule planAndExecutionMustNotShareInternals() {
        DescribedPredicate<JavaClass> planInternals = internalsOf("plan");
        DescribedPredicate<JavaClass> executionInternals = internalsOf("core");

        return CompositeArchRule.of(
                        noClasses().that().resideInAPackage("com.tms.core..")
                                .should().dependOnClassesThat(planInternals)
                                .because("红线 [10] R-1：执行域不得触碰计划表内部模型，联动只走 com.tms.plan.api"))
                .and(noClasses().that().resideInAPackage("com.tms.plan..")
                        .should().dependOnClassesThat(executionInternals)
                        .because("红线 [10] R-1：计划表不得触碰执行域内部模型（反向同样禁止）"));
    }

    /** 某模块"非 api 部分"的目标类谓词。 */
    private static DescribedPredicate<JavaClass> internalsOf(String module) {
        return resideInAPackage("com.tms." + module + "..")
                .and(DescribedPredicate.not(resideInAPackage("com.tms." + module + ".api..")))
                .as("%s 模块非 api 包", module);
    }
}

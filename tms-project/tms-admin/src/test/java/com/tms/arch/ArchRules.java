package com.tms.arch;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.CompositeArchRule;

import java.util.List;

import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * 红线规则的<b>唯一定义处</b>。
 *
 * <p>规则单独抽出来是为了让"故意违规的样例"能验证规则本身有效
 * （见 {@code ArchRulesCounterexampleTest}）。规则和它的验证写在一起、只对真实代码跑一次，
 * 就无法区分"代码没问题"和"规则根本没在检查"——后者更常见，也更难发现。
 *
 * <p>这几条只覆盖<b>代码结构</b>层面的红线。SQL 里的跨表 join（[10] R-1 的后半句）
 * 靠字符串扫描，ArchUnit 看不见，别以为过了这些测试就等于 R-1 全守住。
 */
public final class ArchRules {

    /**
     * 有独立服务边界的业务模块；tms-common / tms-framework 是共享库，不在此列。
     *
     * <p>这里的名字同时是<b>包名片段</b>（{@code com.tms.<name>}）和<b>模块名</b>（{@code tms-<name>}）。
     * 运输执行域叫 {@code core}：artifactId 为 {@code tms-core}，包为 {@code com.tms.core}。
     */
    public static final List<String> BUSINESS_MODULES = List.of("auth", "base", "plan", "core");

    private ArchRules() {
    }

    /**
     * [10] R-1：计划表与运输执行域不得互相依赖内部模型。
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
                                .because("红线 [10] R-1：运单域不得触碰计划表内部模型，联动只走 com.tms.plan.api"))
                .and(noClasses().that().resideInAPackage("com.tms.plan..")
                        .should().dependOnClassesThat(executionInternals)
                        .because("红线 [10] R-1：计划表不得触碰运单域内部模型（反向同样禁止）"));
    }

    /**
     * [05-系统架构] §3 补充 A-3：跨模块只能依赖对方的 {@code api/} 包。
     *
     * <p>这是阶段 2 拆服务"调用方代码零改动"的全部依据。违反一次，那个模块就绑死在进程内。
     */
    public static ArchRule moduleMustOnlyUseOtherModuleApi(String module) {
        return noClasses().that().resideInAPackage("com.tms." + module + "..")
                .should().dependOnClassesThat(internalsOfOtherModules(module))
                .because("A-3：跨模块只能依赖对方 api/ 包，Entity/Mapper/Service 实现一律不得跨模块引用");
    }

    /** 某模块"非 api 部分"的目标类谓词。 */
    private static DescribedPredicate<JavaClass> internalsOf(String module) {
        return resideInAPackage("com.tms." + module + "..")
                .and(DescribedPredicate.not(resideInAPackage("com.tms." + module + ".api..")))
                .as("%s 模块非 api 包", module);
    }

    private static DescribedPredicate<JavaClass> internalsOfOtherModules(String module) {
        return BUSINESS_MODULES.stream()
                .filter(other -> !other.equals(module))
                .map(ArchRules::internalsOf)
                // 用 lambda 而非 DescribedPredicate::or：后者同时存在实例方法与静态可变参数版本，
                // 方法引用会在两者之间歧义（编译期报"引用不明确"）。
                .reduce((left, right) -> left.or(right))
                .orElseThrow(() -> new IllegalStateException("没有其他模块？模块列表配置有误"));
    }
}

package com.tms.arch;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 对<b>真实代码</b>跑红线规则。
 *
 * <p>只管"当前代码是否合规"，不证明规则有效——规则本身有没有检查能力，
 * 由 {@code ArchRulesCounterexampleTest} 用故意违规的样例证明。两件事必须分开测：
 * 合在一起时，"规则写错了导致永远通过"和"代码确实干净"会给出同样的绿灯。
 */
class ArchitectureRulesTest {

    private static JavaClasses productionClasses;

    @BeforeAll
    static void importProductionClasses() {
        productionClasses = new ClassFileImporter()
                // 排除 test-classes：违规样例住在 com.tms.core.mapper 等真实包名下，
                // 不排除的话它们会被当成生产代码，这两条测试就永远红。
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.tms");
    }

    @Test
    @DisplayName("R-1：计划表与运输执行域不共享内部模型")
    void planAndExecutionMustNotShareInternals() {
        ArchRules.planAndExecutionMustNotShareInternals().check(productionClasses);
    }

    @Test
    @DisplayName("A-3：跨模块只能依赖对方 api/ 包")
    void crossModuleMustOnlyUseApiPackages() {
        for (String module : ArchRules.BUSINESS_MODULES) {
            ArchRules.moduleMustOnlyUseOtherModuleApi(module).check(productionClasses);
        }
    }
}

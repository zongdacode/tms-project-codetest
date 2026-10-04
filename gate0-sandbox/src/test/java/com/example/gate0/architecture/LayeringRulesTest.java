package com.example.gate0.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Gate 0 关键项：ArchUnit 1.5.1 能否在 Boot 4 / JUnit 5 测试栈下跑通。
 * <p>规则为沙箱演示版；正式工程按 [12-execution-plan] §3.3 换成
 * tms-plan / tms-tms 互不依赖 Mapper、计划表字段白名单等真实红线。
 */
class LayeringRulesTest {

    private static JavaClasses classes;

    @BeforeAll
    static void importClasses() {
        classes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.example.gate0");
    }

    @Test
    void entityMustNotDependOnServiceOrController() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("..entity..")
                .should().dependOnClassesThat().resideInAnyPackage("..service..", "..controller..");
        rule.check(classes);
    }

    @Test
    void mapperMustNotDependOnService() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("..mapper..")
                .should().dependOnClassesThat().resideInAnyPackage("..service..");
        rule.check(classes);
    }
}

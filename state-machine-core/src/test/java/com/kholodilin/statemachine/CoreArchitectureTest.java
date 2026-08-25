package com.kholodilin.statemachine;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

@AnalyzeClasses(packages = "com.kholodilin.statemachine", importOptions = ImportOption.DoNotIncludeTests.class)
class CoreArchitectureTest {

    @ArchTest
    static final ArchRule noSpring =
            noClasses().should().dependOnClassesThat().resideInAnyPackage("org.springframework..");

    @ArchTest
    static final ArchRule noJdbc = noClasses()
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("java.sql..", "javax.sql..", "org.postgresql..");

    @ArchTest
    static final ArchRule noKafka = noClasses()
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("org.apache.kafka..", "org.springframework.kafka..");
}

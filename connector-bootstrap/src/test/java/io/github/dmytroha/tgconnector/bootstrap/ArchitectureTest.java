package io.github.dmytroha.tgconnector.bootstrap;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;

/**
 * Guards the Clean Architecture dependency rule: dependencies point inwards only.
 */
@AnalyzeClasses(packages = "io.github.dmytroha.tgconnector", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    private static final String ROOT = "io.github.dmytroha.tgconnector";

    @ArchTest
    static final ArchRule layers = layeredArchitecture().consideringOnlyDependenciesInLayers()
            .layer("Domain").definedBy(ROOT + ".domain..")
            .layer("Application").definedBy(ROOT + ".application..")
            .layer("Infrastructure").definedBy(ROOT + ".infrastructure..")
            .layer("Bootstrap").definedBy(ROOT + ".bootstrap..")
            .whereLayer("Bootstrap").mayNotBeAccessedByAnyLayer()
            .whereLayer("Infrastructure").mayOnlyBeAccessedByLayers("Bootstrap")
            .whereLayer("Application").mayOnlyBeAccessedByLayers("Infrastructure", "Bootstrap")
            .whereLayer("Domain").mayOnlyBeAccessedByLayers("Application", "Infrastructure", "Bootstrap");

    @ArchTest
    static final ArchRule coreIsFrameworkFree = noClasses()
            .that().resideInAnyPackage(ROOT + ".domain..", ROOT + ".application..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "org.springframework..", "org.telegram..", "jakarta..", "com.fasterxml..", "tools.jackson..", "org.jsoup..");

    @ArchTest
    static final ArchRule inboundAdaptersUsePortsNotOutboundAdapters = noClasses()
            .that().resideInAPackage(ROOT + ".infrastructure.adapter.in..")
            .should().dependOnClassesThat().resideInAPackage(ROOT + ".infrastructure.adapter.out..");
}

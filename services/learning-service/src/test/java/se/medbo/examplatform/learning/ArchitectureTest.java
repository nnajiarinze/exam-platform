package se.medbo.examplatform.learning;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

@AnalyzeClasses(packages = "se.medbo.examplatform.learning", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {
    @ArchTest
    static final ArchRule no_other_service_source_dependencies = noClasses()
            .should().dependOnClassesThat().resideInAnyPackage("..contentservice..", "..aiservice..");

    @ArchTest
    static final ArchRule content_projection_does_not_depend_on_practice = noClasses()
            .that().resideInAPackage("..contentprojection..")
            .should().dependOnClassesThat().resideInAPackage("..practice..");

    @ArchTest
    static final ArchRule mock_exam_does_not_depend_on_practice_implementation = noClasses()
            .that().resideInAPackage("..mockexam..")
            .should().dependOnClassesThat().resideInAPackage("..practice..");

    @ArchTest
    static final ArchRule controllers_do_not_expose_jdbc_types = classes()
            .that().haveSimpleNameEndingWith("Controller")
            .should().onlyDependOnClassesThat().resideOutsideOfPackage("org.springframework.jdbc..");
}

package eu.poc.taskmanagement.architecture;

import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.junit.ArchTests;
import com.tngtech.archunit.library.dependencies.SlicesRuleDefinition;
import com.tngtech.archunit.core.importer.ImportOption;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

@AnalyzeClasses(
        packages = "eu.poc.taskmanagement",
        importOptions = {ImportOption.DoNotIncludeTests.class}
)
class PackageBoundariesArchTest {

    @ArchTest
    static final ArchTests modelIsIndependent = ArchTests.in(ModelBoundaryRules.class);

    @ArchTest
    static final ArchTests projectionIsReadSideOnly = ArchTests.in(ProjectionBoundaryRules.class);

    @ArchTest
    static final ArchTests integrationIsAdapterOnly = ArchTests.in(IntegrationBoundaryRules.class);

    @ArchTest
    static final ArchTests sagaBoundaryRules = ArchTests.in(SagaBoundaryRules.class);

    @ArchTest
    static final com.tngtech.archunit.lang.ArchRule noCyclesAcrossTopLevelPackages =
            SlicesRuleDefinition.slices()
                    .matching("eu.poc.taskmanagement.(*)..")
                    .should()
                    .beFreeOfCycles();

    static class ModelBoundaryRules {
        @ArchTest
        static final com.tngtech.archunit.lang.ArchRule modelDoesNotDependOnOuterLayers =
                noClasses()
                        .that()
                        .resideInAPackage("..model..")
                        .should()
                        .dependOnClassesThat()
                        .resideInAnyPackage("..api..", "..projection..", "..integration..", "..config..", "..saga..");
    }

    static class ProjectionBoundaryRules {
        @ArchTest
        static final com.tngtech.archunit.lang.ArchRule projectionDoesNotDependOnApiOrInfrastructure =
                noClasses()
                        .that()
                        .resideInAPackage("..projection..")
                        .should()
                        .dependOnClassesThat()
                        .resideInAnyPackage("..api..", "..integration..", "..config..", "..saga..");
    }

    static class IntegrationBoundaryRules {
        @ArchTest
        static final com.tngtech.archunit.lang.ArchRule integrationDoesNotDependOnDomainOrReadModelEntrypoints =
                noClasses()
                        .that()
                        .resideInAPackage("..integration..")
                        .should()
                        .dependOnClassesThat()
                        .resideInAnyPackage("..api..", "..projection..", "..saga..");
    }

    static class SagaBoundaryRules {
        @ArchTest
        static final com.tngtech.archunit.lang.ArchRule sagaDoesNotDependOnApiOrReadModel =
                noClasses()
                        .that()
                        .resideInAPackage("..saga..")
                        .should()
                        .dependOnClassesThat()
                        .resideInAnyPackage("..api..", "..projection..");
    }
}

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
    static final ArchTests apiBoundaryRules = ArchTests.in(ApiBoundaryRules.class);

    @ArchTest
    static final ArchTests applicationBoundaryRules = ArchTests.in(ApplicationBoundaryRules.class);

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
                        .resideInAPackage("eu.poc.taskmanagement.model..")
                        .should()
                        .dependOnClassesThat()
                        .resideInAnyPackage(
                                "eu.poc.taskmanagement.api..",
                                "eu.poc.taskmanagement.projection..",
                                "eu.poc.taskmanagement.integration..",
                                "eu.poc.taskmanagement.config..",
                                "eu.poc.taskmanagement.saga..",
                                "eu.poc.taskmanagement.application..");
    }

    static class ProjectionBoundaryRules {
        @ArchTest
        static final com.tngtech.archunit.lang.ArchRule projectionDoesNotDependOnApiOrInfrastructure =
                noClasses()
                        .that()
                        .resideInAPackage("eu.poc.taskmanagement.projection..")
                        .should()
                        .dependOnClassesThat()
                        .resideInAnyPackage(
                                "eu.poc.taskmanagement.api..",
                                "eu.poc.taskmanagement.integration..",
                                "eu.poc.taskmanagement.config..",
                                "eu.poc.taskmanagement.saga..");
    }

    static class ApiBoundaryRules {
        @ArchTest
        static final com.tngtech.archunit.lang.ArchRule apiHttpDoesNotDependOnDomainOrInfrastructure =
                noClasses()
                        .that()
                        .resideInAPackage("eu.poc.taskmanagement.api.http..")
                        .should()
                        .dependOnClassesThat()
                        .resideInAnyPackage(
                                "eu.poc.taskmanagement.model..",
                                "eu.poc.taskmanagement.projection..",
                                "eu.poc.taskmanagement.integration..",
                                "eu.poc.taskmanagement.saga..",
                                "eu.poc.taskmanagement.config..");
    }

    static class ApplicationBoundaryRules {
        @ArchTest
        static final com.tngtech.archunit.lang.ArchRule applicationDoesNotDependOnApiHttp =
                noClasses()
                        .that()
                        .resideInAPackage("eu.poc.taskmanagement.application..")
                        .should()
                        .dependOnClassesThat()
                        .resideInAnyPackage("eu.poc.taskmanagement.api.http..");
    }

    static class IntegrationBoundaryRules {
        @ArchTest
        static final com.tngtech.archunit.lang.ArchRule integrationDoesNotDependOnDomainOrReadModelEntrypoints =
                noClasses()
                        .that()
                        .resideInAPackage("eu.poc.taskmanagement.integration..")
                        .should()
                        .dependOnClassesThat()
                        .resideInAnyPackage(
                                "eu.poc.taskmanagement.api..",
                                "eu.poc.taskmanagement.projection..",
                                "eu.poc.taskmanagement.saga..");
    }

    static class SagaBoundaryRules {
        @ArchTest
        static final com.tngtech.archunit.lang.ArchRule sagaDoesNotDependOnApiOrReadModel =
                noClasses()
                        .that()
                        .resideInAPackage("eu.poc.taskmanagement.saga..")
                        .should()
                        .dependOnClassesThat()
                        .resideInAnyPackage("eu.poc.taskmanagement.api..", "eu.poc.taskmanagement.projection..");
    }
}

package com.fincore.architecture

import com.tngtech.archunit.core.domain.JavaClasses
import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.core.importer.ImportOption
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.fields
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * Module boundaries are text until something enforces them.
 *
 * BACKEND-ARCHITECTURE.md §3 names this as the largest architectural risk in
 * Phase 1: without these rules the modular monolith decays into the layered ball
 * of mud that ADR-006 explicitly rejects — silently, with no individual bad
 * commit.
 */
class ArchitectureTest {

    companion object {
        private lateinit var classes: JavaClasses

        @JvmStatic
        @BeforeAll
        fun importClasses() {
            classes = ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.fincore")
        }

        /** Domain modules, per BACKEND-ARCHITECTURE.md §1. */
        private val DOMAIN_MODULES = listOf(
            "identity", "accounts", "transactions",
            "payments", "customer", "notifications", "audit",
        )
    }

    @Test
    @DisplayName("no double or float field anywhere — money must never touch binary floating point")
    fun noFloatingPointFields() {
        // ADR-012. This is the structural enforcement; MoneyTest proves the
        // behaviour. A `double amount` field would defeat both.
        fields().that().areDeclaredInClassesThat().resideInAPackage("com.fincore..")
            .should().notHaveRawType(Double::class.javaPrimitiveType)
            .andShould().notHaveRawType(Float::class.javaPrimitiveType)
            .andShould().notHaveRawType(java.lang.Double::class.java)
            .andShould().notHaveRawType(java.lang.Float::class.java)
            .because("monetary values must use BigDecimal (ADR-012)")
            .allowEmptyShould(true)
            .check(classes)
    }

    @Test
    @DisplayName("domain layers do not depend on infrastructure")
    fun domainDoesNotDependOnInfrastructure() {
        noClasses().that().resideInAPackage("com.fincore..domain..")
            .should().dependOnClassesThat().resideInAPackage("com.fincore..infrastructure..")
            .because("the domain layer must not know how it is persisted (ADR-002)")
            .allowEmptyShould(true)
            .check(classes)
    }

    @Test
    @DisplayName("no module reaches into another module's infrastructure")
    fun modulesDoNotCrossIntoForeignInfrastructure() {
        // Modules communicate through application service interfaces only.
        // No JPA repository is accessed across a module boundary.
        DOMAIN_MODULES.forEach { module ->
            val foreign = DOMAIN_MODULES.filter { it != module }
            foreign.forEach { other ->
                noClasses().that().resideInAPackage("com.fincore.$module..")
                    .should().dependOnClassesThat().resideInAPackage("com.fincore.$other.infrastructure..")
                    .because("module '$module' must not reach into '$other' infrastructure (ADR-006)")
                    .allowEmptyShould(true)
                    .check(classes)
            }
        }
    }

    @Test
    @DisplayName("shared depends on no domain module")
    fun sharedDependsOnNothing() {
        DOMAIN_MODULES.forEach { module ->
            noClasses().that().resideInAPackage("com.fincore.shared..")
                .should().dependOnClassesThat().resideInAPackage("com.fincore.$module..")
                .because("shared/ is depended upon by everything and must depend on nothing")
                .allowEmptyShould(true)
                .check(classes)
        }
    }

    @Test
    @DisplayName("controllers are not called from application services")
    fun applicationDoesNotDependOnApi() {
        noClasses().that().resideInAPackage("com.fincore..application..")
            .should().dependOnClassesThat().resideInAPackage("com.fincore..api..")
            .because("dependencies point inward, never outward to the API layer")
            .allowEmptyShould(true)
            .check(classes)
    }
}

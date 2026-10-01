// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.authz;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.junit.ArchTests;
import com.tngtech.archunit.lang.ArchRule;
import org.devlive.grantforge.testsupport.ArchitectureRules;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/** Applies the shared architecture rules to the authorization module. */
@AnalyzeClasses(packages = "org.devlive.grantforge.authz", importOptions = ImportOption.DoNotIncludeTests.class)
final class ArchitectureTest
{
    @ArchTest
    static final ArchTests SHARED_RULES = ArchTests.in(ArchitectureRules.class);

    @ArchTest
    static final ArchRule DOMAIN_DOES_NOT_USE_THE_APPLICATION_LAYER = noClasses()
            .that().resideInAPackage("..authz.domain..")
            .should().dependOnClassesThat().resideInAPackage("..authz.application..")
            .because("entities and repositories must not depend on the use cases built on them");

    // ArchUnit reads static rule fields reflectively and never instantiates this class.
    private ArchitectureTest()
    {
    }
}

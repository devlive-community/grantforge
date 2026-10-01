// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.common;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.junit.ArchTests;
import com.tngtech.archunit.lang.ArchRule;
import org.devlive.grantforge.testsupport.ArchitectureRules;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/** Applies the shared rules and keeps the common module free of framework dependencies. */
@AnalyzeClasses(packages = "org.devlive.grantforge.common", importOptions = ImportOption.DoNotIncludeTests.class)
final class ArchitectureTest
{
    @ArchTest
    static final ArchTests SHARED_RULES = ArchTests.in(ArchitectureRules.class);

    @ArchTest
    static final ArchRule FRAMEWORK_FREE = noClasses()
            .should().dependOnClassesThat().resideInAnyPackage("org.springframework..", "jakarta..", "org.hibernate..")
            .because("grantforge-common is used by every module, including agents that do not run Spring");

    // ArchUnit reads static rule fields reflectively and never instantiates this class.
    private ArchitectureTest()
    {
    }
}

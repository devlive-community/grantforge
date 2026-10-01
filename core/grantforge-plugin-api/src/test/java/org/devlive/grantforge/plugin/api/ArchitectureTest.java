// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.plugin.api;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.junit.ArchTests;
import com.tngtech.archunit.lang.ArchRule;
import org.devlive.grantforge.testsupport.ArchitectureRules;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/** Applies the shared rules and keeps the plugin API free of anything a plugin would have to share. */
@AnalyzeClasses(packages = "org.devlive.grantforge.plugin.api", importOptions = ImportOption.DoNotIncludeTests.class)
final class ArchitectureTest
{
    @ArchTest
    static final ArchTests SHARED_RULES = ArchTests.in(ArchitectureRules.class);

    @ArchTest
    static final ArchRule ONLY_JDK_AND_JSPECIFY = classes()
            .should().onlyDependOnClassesThat().resideInAnyPackage("java..", "org.jspecify..",
                    "org.devlive.grantforge.plugin.api..")
            .because("plugins load the API from the server's class loader, so it must not drag other libraries in");

    @ArchTest
    static final ArchRule MODEL_DOES_NOT_KNOW_THE_SPI = noClasses()
            .that().resideInAPackage("..plugin.api.model..")
            .should().dependOnClassesThat().resideInAPackage("org.devlive.grantforge.plugin.api")
            .because("definitions are plain data the server stores and sends to the console");

    // ArchUnit reads static rule fields reflectively and never instantiates this class.
    private ArchitectureTest()
    {
    }
}

// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.datasource;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Prevents provider details from leaking back into authentication or generic organization reconciliation.
 */
@AnalyzeClasses(packages = {"org.devlive.grantforge.datasource", "org.devlive.grantforge.identity"}, importOptions = ImportOption.DoNotIncludeTests.class)
final class ArchitectureTest
{
    @ArchTest
    static final ArchRule IDENTITY_IS_INDEPENDENT = noClasses().that().resideInAPackage("org.devlive.grantforge.identity..")
        .should().dependOnClassesThat().resideInAPackage("org.devlive.grantforge.datasource..");

    @ArchTest
    static final ArchRule SDK_STAYS_IN_ADAPTER = noClasses().that().resideOutsideOfPackage("org.devlive.grantforge.datasource.provider.dingtalk..")
        .should().dependOnClassesThat().resideInAnyPackage("com.dingtalk..", "com.taobao..");

    private ArchitectureTest()
    {
    }
}

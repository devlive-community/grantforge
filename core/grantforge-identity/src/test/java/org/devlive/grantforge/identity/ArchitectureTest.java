// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.identity;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.junit.ArchTests;
import org.devlive.grantforge.testsupport.ArchitectureRules;

/** Applies the shared architecture rules to the identity module. */
@AnalyzeClasses(packages = "org.devlive.grantforge.identity", importOptions = ImportOption.DoNotIncludeTests.class)
final class ArchitectureTest
{
    @ArchTest
    static final ArchTests SHARED_RULES = ArchTests.in(ArchitectureRules.class);

    // ArchUnit reads static rule fields reflectively and never instantiates this class.
    private ArchitectureTest()
    {
    }
}

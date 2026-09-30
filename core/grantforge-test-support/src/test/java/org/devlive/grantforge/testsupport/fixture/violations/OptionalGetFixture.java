// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.testsupport.fixture.violations;

import java.util.Optional;

/** Violates NO_OPTIONAL_GET. */
public class OptionalGetFixture
{
    String value()
    {
        return Optional.of("fixture").get();
    }
}

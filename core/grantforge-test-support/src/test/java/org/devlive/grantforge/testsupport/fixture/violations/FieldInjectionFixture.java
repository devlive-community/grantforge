// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.testsupport.fixture.violations;

import org.springframework.beans.factory.annotation.Autowired;

/** Violates NO_FIELD_INJECTION. */
public class FieldInjectionFixture
{
    @Autowired
    private Object dependency;

    Object dependency()
    {
        return dependency;
    }
}

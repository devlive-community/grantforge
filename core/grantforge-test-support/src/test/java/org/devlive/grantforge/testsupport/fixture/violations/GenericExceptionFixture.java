// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.testsupport.fixture.violations;

/** Violates NO_GENERIC_EXCEPTIONS. */
public class GenericExceptionFixture
{
    void fail()
    {
        throw new RuntimeException("fixture");
    }
}

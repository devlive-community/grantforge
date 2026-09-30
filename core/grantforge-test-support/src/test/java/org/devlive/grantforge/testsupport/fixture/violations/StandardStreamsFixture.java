// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.testsupport.fixture.violations;

/** Violates NO_STANDARD_STREAMS. */
public class StandardStreamsFixture
{
    void print()
    {
        System.out.println("fixture");
    }
}

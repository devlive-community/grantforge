// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.testsupport.fixture.clean;

/** A class without fields, like a module that only has an application entry point. */
public final class StatelessFixture
{
    private StatelessFixture()
    {
    }

    /**
     * Returns a constant.
     *
     * @return always {@code 1}
     */
    public static int one()
    {
        return 1;
    }
}

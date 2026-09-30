// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.testsupport.fixture.violations;

import java.util.logging.Logger;

/** Violates NO_JAVA_UTIL_LOGGING. */
public class JavaUtilLoggingFixture
{
    private static final Logger LOGGER = Logger.getLogger("fixture");

    void log()
    {
        LOGGER.info("fixture");
    }
}

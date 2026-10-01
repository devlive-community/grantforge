// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.testsupport.fixture.violations.api;

import org.devlive.grantforge.testsupport.fixture.violations.SampleJpaEntity;

/** Violates ENTITIES_STAY_OUT_OF_API by returning an entity from API code. */
public class EntityLeakFixture
{
    SampleJpaEntity get()
    {
        return new SampleJpaEntity();
    }
}

// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.testsupport.fixture.clean.api;

/** Returns a DTO instead of an entity. */
public class CleanApiFixture
{
    /**
     * Returns a view.
     *
     * @return the view
     */
    public View get()
    {
        return new View(1L);
    }

    /**
     * Response DTO.
     *
     * @param id the ID
     */
    public record View(long id)
    {
    }
}

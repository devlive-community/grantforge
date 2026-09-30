// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.testsupport.fixture.clean;

import java.util.NoSuchElementException;
import java.util.Optional;

import static java.util.Objects.requireNonNull;

/** Uses constructor injection and explicit Optional handling. */
public class CleanFixture
{
    private final Optional<String> name;

    /**
     * Creates the fixture.
     *
     * @param name optional name
     */
    public CleanFixture(Optional<String> name)
    {
        this.name = requireNonNull(name, "name");
    }

    /**
     * Returns the name.
     *
     * @return the name
     * @throws NoSuchElementException when no name is present
     */
    public String name()
    {
        return name.orElseThrow(() -> new NoSuchElementException("name"));
    }
}

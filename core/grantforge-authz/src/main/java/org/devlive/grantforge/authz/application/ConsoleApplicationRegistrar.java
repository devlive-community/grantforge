// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.authz.application;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

import static java.util.Objects.requireNonNull;

/**
 * Makes sure the console's own application exists in the catalog at start-up (D-40); its resources are declared
 * by the code and synchronized later (M5-02, M5-04).
 */
@Component
public final class ConsoleApplicationRegistrar
        implements ApplicationRunner
{
    private final ApplicationService applications;

    /**
     * Creates the registrar.
     *
     * @param applications the catalog's applications
     */
    public ConsoleApplicationRegistrar(ApplicationService applications)
    {
        this.applications = requireNonNull(applications, "applications");
    }

    @Override
    public void run(ApplicationArguments args)
    {
        try {
            applications.registerConsole();
        }
        catch (DataIntegrityViolationException race) {
            // Another node registered it at the same moment; one row is all that is needed.
            applications.registerConsole();
        }
    }
}

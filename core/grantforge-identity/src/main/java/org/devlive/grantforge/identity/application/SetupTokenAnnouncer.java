// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.identity.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import static java.util.Objects.requireNonNull;

/**
 * Issues a setup token when the application is ready and setup is pending, and tells the operator how to
 * finish setup. The generated token is written to the log because the operator reading the log is the
 * person entitled to set up the server.
 */
@Component
public final class SetupTokenAnnouncer
{
    private static final Logger LOG = LoggerFactory.getLogger(SetupTokenAnnouncer.class);

    private final SetupService setup;

    /**
     * Creates the announcer.
     *
     * @param setup the setup service
     */
    public SetupTokenAnnouncer(SetupService setup)
    {
        this.setup = requireNonNull(setup, "setup");
    }

    /** Issues and announces the token; a no-op once setup is complete. */
    @EventListener(ApplicationReadyEvent.class)
    public void announce()
    {
        if (!setup.isRequired()) {
            return;
        }
        setup.issueToken().ifPresentOrElse(
                token -> LOG.warn("First-run setup is pending. Open the console and enter this setup token: {}"
                        + " (it changes on every restart until setup completes)", token),
                () -> LOG.warn("First-run setup is pending. Open the console and enter the configured setup"
                        + " token (grantforge.setup.token)."));
    }
}

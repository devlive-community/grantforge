// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.agent.sync;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Says each thing once. A sync runs every few seconds and finds the same user it leaves alone or the same entry it
 * cannot read every time; the log names it the first time only, until the agent restarts.
 */
public final class Notices
{
    // The name, not the logger: the agent's own static field holds the logger, which LogManager returns by name.
    private final String log;
    private final Set<String> said = ConcurrentHashMap.newKeySet();

    /**
     * Creates the notices of one part of an agent.
     *
     * @param log the logger they are written to, the agent's own
     */
    public Notices(Logger log)
    {
        this.log = log.getName();
    }

    /**
     * Logs a message unless it was logged before.
     *
     * @param level how important it is
     * @param message the message
     * @return whether it was logged now
     */
    public boolean once(Level level, String message)
    {
        if (!said.add(message)) {
            return false;
        }
        Logger.getLogger(log).log(level, message);
        return true;
    }
}

// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.mysql.agent.sql;

import org.devlive.grantforge.agent.AccessEvent;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * What the proxy does with what a client sent: send it on as it is, send a rewritten text instead, or refuse it; with
 * the access events of the checks.
 *
 * @param sql the text to send in place of the client's, or {@code null} to send the client's as it is
 * @param refusal why it is refused, or {@code null} when it may go on
 * @param events the access events to report
 */
public record Verdict(@Nullable String sql, @Nullable Refusal refusal, List<AccessEvent> events)
{
    /** Copies the events. */
    public Verdict
    {
        events = List.copyOf(events);
    }

    /**
     * Returns whether the statements may reach the server.
     *
     * @return {@code true} unless refused
     */
    public boolean allowed()
    {
        return refusal == null;
    }
}

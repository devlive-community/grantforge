// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.identity.application;

import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** A {@link SessionTerminator} for tests that records what it was asked to end. */
public final class RecordingSessionTerminator
        implements SessionTerminator
{
    private final List<String> sessions = new CopyOnWriteArrayList<>();
    private final List<Long> accounts = new CopyOnWriteArrayList<>();
    private final List<String> kept = new CopyOnWriteArrayList<>();

    @Override
    public void terminate(String sessionId)
    {
        sessions.add(sessionId);
    }

    @Override
    public void terminateAllOf(long accountId, @Nullable String keep)
    {
        accounts.add(accountId);
        kept.add(keep == null ? "" : keep);
    }

    /**
     * Returns the sessions kept while ending all others, {@code ""} where none was kept.
     *
     * @return the kept session IDs, in order
     */
    public List<String> kept()
    {
        return List.copyOf(kept);
    }

    /**
     * Returns the ended sessions.
     *
     * @return the session IDs, in order
     */
    public List<String> sessions()
    {
        return List.copyOf(sessions);
    }

    /**
     * Returns the accounts whose sessions were all ended.
     *
     * @return the account IDs, in order
     */
    public List<Long> accounts()
    {
        return List.copyOf(accounts);
    }

    /** Forgets what was recorded. */
    public void clear()
    {
        sessions.clear();
        accounts.clear();
        kept.clear();
    }
}

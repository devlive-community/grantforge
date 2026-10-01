// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.security;

import org.devlive.grantforge.identity.application.SessionTerminator;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.stereotype.Component;

import static java.util.Objects.requireNonNull;

/**
 * Ends console sessions in Spring Session's store. A session's principal name is its account ID
 * ({@link SessionUser#getName()}), so all sessions of an account are found through Spring Session's index.
 * Without a web server (for example a command-line run) there is no session store and nothing to end.
 */
@Component
public final class SpringSessionTerminator
        implements SessionTerminator
{
    private final ObjectProvider<FindByIndexNameSessionRepository<? extends Session>> sessions;

    /**
     * Creates the terminator.
     *
     * @param sessions Spring Session's repository, absent without a web server
     */
    public SpringSessionTerminator(ObjectProvider<FindByIndexNameSessionRepository<? extends Session>> sessions)
    {
        this.sessions = requireNonNull(sessions, "sessions");
    }

    @Override
    public void terminate(String sessionId)
    {
        sessions.ifAvailable(store -> store.deleteById(sessionId));
    }

    @Override
    public void terminateAllOf(long accountId)
    {
        sessions.ifAvailable(store -> store.findByPrincipalName(Long.toString(accountId)).keySet()
                .forEach(store::deleteById));
    }
}

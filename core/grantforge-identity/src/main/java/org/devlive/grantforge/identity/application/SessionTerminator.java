// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.identity.application;

import org.jspecify.annotations.Nullable;

/**
 * Ends console sessions in the session store, so their cookies stop working at the next request. Implemented
 * by the server on top of Spring Session.
 */
public interface SessionTerminator
{
    /**
     * Ends one session; does nothing if it already ended.
     *
     * @param sessionId Spring Session's ID of the session
     */
    void terminate(String sessionId);

    /**
     * Ends every session of an account, including sessions missing from the session index.
     *
     * @param accountId the account
     * @param keep Spring Session's ID of a session to leave running (the request's own), or {@code null}
     */
    void terminateAllOf(long accountId, @Nullable String keep);
}

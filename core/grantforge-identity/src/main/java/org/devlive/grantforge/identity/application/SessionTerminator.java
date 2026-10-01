// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.identity.application;

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
     */
    void terminateAllOf(long accountId);
}

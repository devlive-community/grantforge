// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.agent;

import org.devlive.grantforge.service.agent.IssuedToken;

/**
 * A token just issued, with its secret: shown this once.
 *
 * @param token the token as listed
 * @param secret what agents sign in with
 */
public record IssuedTokenResponse(AgentTokenResponse token, String secret)
{
    /**
     * Converts an issued token.
     *
     * @param issued the token
     * @return the response
     */
    public static IssuedTokenResponse from(IssuedToken issued)
    {
        return new IssuedTokenResponse(AgentTokenResponse.from(issued.token()), issued.secret());
    }

    @Override
    public String toString()
    {
        return "IssuedTokenResponse[token=" + token + ", secret=***]";
    }
}

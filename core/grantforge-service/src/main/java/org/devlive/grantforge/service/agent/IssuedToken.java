// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.service.agent;

/**
 * A token just issued: the only time its secret is known.
 *
 * @param token the token as listed
 * @param secret the token agents sign in with
 */
public record IssuedToken(AgentTokenView token, String secret)
{
    @Override
    public String toString()
    {
        return "IssuedToken[token=" + token + ", secret=***]";
    }
}

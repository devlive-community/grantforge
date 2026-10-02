// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.service;

import org.devlive.grantforge.plugin.api.ConnectionResult;
import org.jspecify.annotations.Nullable;

/**
 * How a connection test went.
 *
 * @param status succeeded, failed, or not supported by the plugin
 * @param message what the plugin said, if anything
 */
public record ConnectionResponse(ConnectionResult.Status status, @Nullable String message)
{
    /**
     * Converts a result.
     *
     * @param result the result
     * @return the response
     */
    public static ConnectionResponse from(ConnectionResult result)
    {
        return new ConnectionResponse(result.status(), result.message());
    }
}

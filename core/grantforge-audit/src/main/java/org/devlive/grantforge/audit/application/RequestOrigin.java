// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.audit.application;

import org.jspecify.annotations.Nullable;

/**
 * Where a request came from, as audit events record it.
 *
 * @param requestId the request's correlation ID, if any
 * @param clientIp the client's address, if known
 * @param userAgent the client's user agent, if sent
 */
public record RequestOrigin(@Nullable String requestId, @Nullable String clientIp, @Nullable String userAgent)
{
    /** The origin of work that no request started, such as start-up tasks. */
    public static final RequestOrigin NONE = new RequestOrigin(null, null, null);
}

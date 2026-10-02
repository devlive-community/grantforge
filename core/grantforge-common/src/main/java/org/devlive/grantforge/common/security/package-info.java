// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

/**
 * Declarations of who may call an HTTP endpoint. Every endpoint of the API carries exactly one of
 * {@link org.devlive.grantforge.common.security.PublicEndpoint},
 * {@link org.devlive.grantforge.common.security.AuthenticatedEndpoint} or
 * {@link org.devlive.grantforge.common.security.RequirePermission}; the server refuses to start otherwise.
 */
@NullMarked
package org.devlive.grantforge.common.security;

import org.jspecify.annotations.NullMarked;

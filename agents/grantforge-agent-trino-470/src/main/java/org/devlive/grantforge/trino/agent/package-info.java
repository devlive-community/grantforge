// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

/**
 * The GrantForge agent for Trino: a plugin with a system access control, named {@code grantforge}, that checks what a
 * coordinator is asked against the service's signed policies, lists only what a user may use, filters rows, masks
 * columns and reports access events, repeated ones folded into one.
 */
@NullMarked
package org.devlive.grantforge.trino.agent;

import org.jspecify.annotations.NullMarked;

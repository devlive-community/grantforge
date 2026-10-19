// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

/**
 * What the GrantForge agents for Trino share, without Trino's SPI: decisions at every scope of a catalog, schema, table
 * and column against the service's signed policies, listings filtered to what a user may use, row filters and column
 * masks fitted to each column's type, impersonation and the queries of other users under policy, and access events,
 * repeated ones folded into one.
 */
@NullMarked
package org.devlive.grantforge.trino.agent;

import org.jspecify.annotations.NullMarked;

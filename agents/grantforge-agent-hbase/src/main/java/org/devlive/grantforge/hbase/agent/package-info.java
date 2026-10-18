// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

/**
 * The GrantForge agent for HBase: a coprocessor of masters and region servers that checks operations, reads and writes
 * against the service's signed policies, returns only the columns a user may read, hides what a user may not use and
 * reports access events, repeated ones folded into one.
 */
@NullMarked
package org.devlive.grantforge.hbase.agent;

import org.jspecify.annotations.NullMarked;

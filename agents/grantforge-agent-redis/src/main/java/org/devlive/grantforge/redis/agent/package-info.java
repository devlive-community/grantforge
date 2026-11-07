// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

/**
 * The Redis agent: a service beside a Redis or Valkey deployment that keeps each user's ACL selectors on every node in
 * step with the signed policies of a Redis service, and reports refused commands from the nodes' ACL logs.
 */
@NullMarked
package org.devlive.grantforge.redis.agent;

import org.jspecify.annotations.NullMarked;

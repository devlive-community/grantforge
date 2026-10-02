// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

/**
 * Decides access requests against policies, the way the agents in protected systems (HDFS, Hive, ...) do. Plain Java
 * 8 without dependencies, so it can run inside those systems; immutable and thread-safe once built.
 *
 * <p>A {@link org.devlive.grantforge.policy.engine.ServiceModel} describes a service type's resource levels and
 * access types; {@link org.devlive.grantforge.policy.engine.Policy policies} allow and deny access types on
 * resources to users, groups and roles; a {@link org.devlive.grantforge.policy.engine.PolicyEngine} built from both
 * answers {@link org.devlive.grantforge.policy.engine.AccessRequest access requests} with a
 * {@link org.devlive.grantforge.policy.engine.Decision} that names the deciding policy.
 */
@NullMarked
package org.devlive.grantforge.policy.engine;

import org.jspecify.annotations.NullMarked;

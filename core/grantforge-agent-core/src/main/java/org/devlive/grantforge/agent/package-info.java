// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

/**
 * The part every agent shares, whatever system it protects (M13-01, D-84). A
 * {@link org.devlive.grantforge.agent.GrantForgeAgent} keeps the policy snapshot of its service current: it sends
 * heartbeats, downloads a new snapshot when the policy version changes, checks the snapshot's Ed25519 signature and
 * keeps the last good one on disk for when the server cannot be reached. It decides access with the policy engine,
 * adding the roles and groups the snapshot gives the user, and ships access events in batches, spooling them to disk
 * while the server is away.
 *
 * <p>Java 8 on {@link java.net.HttpURLConnection}, with Jackson 2 for JSON and Bouncy Castle for Ed25519, which the JDK
 * has only from Java 15 on. Agents run inside the protected systems, whose class paths bring their own versions of
 * such libraries, so an agent relocates them when it packages itself.
 */
@NullMarked
package org.devlive.grantforge.agent;

import org.jspecify.annotations.NullMarked;

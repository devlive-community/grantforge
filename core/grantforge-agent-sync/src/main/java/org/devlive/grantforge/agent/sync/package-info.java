// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

/**
 * What the agents beside a protected system share. Such an agent never stands in the system's request path: it keeps
 * the system's own access control in step with the signed policies of its service, one sync at a time, and reports
 * accesses from the system's own audit. {@link org.devlive.grantforge.agent.sync.Launcher} runs it from a settings file
 * that {@link org.devlive.grantforge.agent.sync.Settings} reads; {@link org.devlive.grantforge.agent.sync.AgentRuntime}
 * holds the GrantForge agent, the audit and the timers; {@link org.devlive.grantforge.agent.sync.SyncLoop} is the sync
 * every agent runs, with the system's own reading and applying left to it; {@link
 * org.devlive.grantforge.agent.sync.PositionFile} remembers where an audit was read to and {@link
 * org.devlive.grantforge.agent.sync.Notices} says each thing once.
 *
 * <p>The classes log through the loggers of the agents that use them, so an agent's messages keep its own name.
 */
@NullMarked
package org.devlive.grantforge.agent.sync;

import org.jspecify.annotations.NullMarked;

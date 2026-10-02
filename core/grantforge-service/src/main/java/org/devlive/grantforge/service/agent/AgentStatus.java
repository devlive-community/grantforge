// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.service.agent;

/** How an agent is doing, from its last heartbeat. */
public enum AgentStatus
{
    /** It reported recently and applies the current policies. */
    CURRENT,
    /** It reported recently but applies older policies, or none yet. */
    OUTDATED,
    /** It has not reported for a while. */
    SILENT
}

// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.agent;

import org.devlive.grantforge.service.agent.Ingested;

/**
 * What became of a batch of access events.
 *
 * @param accepted the events stored
 * @param duplicates the events stored before
 * @param expired the events older than the retention period, dropped
 */
public record IngestedResponse(int accepted, int duplicates, int expired)
{
    /**
     * Converts the result.
     *
     * @param ingested the result
     * @return the response
     */
    public static IngestedResponse from(Ingested ingested)
    {
        return new IngestedResponse(ingested.accepted(), ingested.duplicates(), ingested.expired());
    }
}

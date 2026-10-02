// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.service.agent;

/**
 * What became of a batch of access events.
 *
 * @param accepted the events stored
 * @param duplicates the events stored before, from an earlier send of the batch
 * @param expired the events older than the retention period, dropped
 */
public record Ingested(int accepted, int duplicates, int expired)
{
}

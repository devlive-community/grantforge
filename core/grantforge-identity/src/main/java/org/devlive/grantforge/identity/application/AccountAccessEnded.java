// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.identity.application;

/**
 * Revokes authorization-server grants in the same transaction as an account's deactivation.
 * @param accountId the disabled account
 */
public record AccountAccessEnded(long accountId)
{
}

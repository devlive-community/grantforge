// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.authz.application;

/**
 * How far a round of an access review got.
 *
 * @param total the assignments under review
 * @param pending those nobody decided about
 * @param keep those to keep
 * @param revoke those to revoke
 * @param revoked those completing the round removed
 */
public record ReviewProgress(long total, long pending, long keep, long revoke, long revoked)
{
    /** Nothing to review. */
    public static final ReviewProgress NONE = new ReviewProgress(0, 0, 0, 0, 0);
}

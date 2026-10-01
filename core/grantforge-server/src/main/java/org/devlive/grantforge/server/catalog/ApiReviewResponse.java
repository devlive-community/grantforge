// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.catalog;

/**
 * The outcome of a review.
 *
 * @param reviewed how many changes were confirmed
 */
public record ApiReviewResponse(int reviewed)
{
}

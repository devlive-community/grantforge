// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.data;

import org.devlive.grantforge.authz.data.DataPreview;

/**
 * How many rows a user would see with only a role, against now.
 *
 * @param withRole the rows the role alone, its inherited roles included, lets the user use
 * @param now the rows the user's roles let them use now
 */
public record DataPreviewResponse(long withRole, long now)
{
    /**
     * Converts a preview.
     *
     * @param preview the preview
     * @return the response
     */
    public static DataPreviewResponse from(DataPreview preview)
    {
        return new DataPreviewResponse(preview.withRole(), preview.now());
    }
}

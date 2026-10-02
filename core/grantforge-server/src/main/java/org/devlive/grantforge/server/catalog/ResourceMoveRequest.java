// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.catalog;

import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;

/**
 * Where to move a resource.
 *
 * @param parentId the new parent, of the same application, or {@code null} for the top level
 * @param position the 0-based position among the new siblings; out-of-range values move to the first or last place
 */
public record ResourceMoveRequest(@Size(max = 20) @Nullable String parentId, int position)
{
}

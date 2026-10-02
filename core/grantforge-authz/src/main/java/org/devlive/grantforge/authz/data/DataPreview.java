// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.authz.data;

/**
 * How many rows of an entity a user would see for an action with only one role, against how many they see now.
 *
 * @param withRole the rows the role alone lets the user use, its inherited roles included
 * @param now the rows the user's roles let them use now
 */
public record DataPreview(long withRole, long now)
{
}

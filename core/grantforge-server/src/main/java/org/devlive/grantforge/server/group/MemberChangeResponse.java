// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.group;

/**
 * The outcome of adding or removing members.
 *
 * @param changed how many accounts actually joined or left; accounts already in the wanted state do not count
 */
public record MemberChangeResponse(int changed)
{
}

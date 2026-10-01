// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.authz.domain;

/**
 * How many resources an application has.
 *
 * @param applicationId the application
 * @param resources the number of its resources
 */
public record ResourceCount(long applicationId, long resources)
{
}

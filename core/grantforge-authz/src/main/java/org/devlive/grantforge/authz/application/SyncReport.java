// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.authz.application;

/**
 * What a synchronization of the API catalog found.
 *
 * @param endpoints how many endpoints the server serves
 * @param added how many appeared
 * @param changed how many changed their access or came back
 * @param removed how many went away
 * @param permissions how many API resources (permissions) were created
 */
public record SyncReport(int endpoints, int added, int changed, int removed, int permissions)
{
}

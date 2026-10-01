// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.authz.application;

/**
 * What a synchronization of the console's manifest changed.
 *
 * @param resources how many resources the manifest declares
 * @param created how many were created
 * @param updated how many got a new name, name key or route
 * @param dependenciesAdded how many declared dependencies were added
 * @param dependenciesRemoved how many declared dependencies the manifest no longer has were removed
 */
public record ManifestReport(int resources, int created, int updated, int dependenciesAdded, int dependenciesRemoved)
{
}

// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.datasource.application;

import org.devlive.grantforge.datasource.provider.DirectoryProvider.Employee;
import org.devlive.grantforge.identity.domain.IdentitySource;

import java.util.List;
import java.util.Map;
import java.util.Set;

/** Resolves directory employees to subjects issued by a configured identity source. */
public interface DirectorySubjectResolver
{
    /**
     * Identifies the setting used to select this resolver.
     * @return the configuration key that enables this resolver
     */
    String setting();

    /**
     * Returns the settings that can change independently of the directory authority.
     * @return resolver configuration keys
     */
    default Set<String> configurationKeys()
    {
        return Set.of(setting());
    }

    /**
     * Resolves subjects before starting the transaction that publishes the snapshot.
     * @param identity the configured identity source
     * @param configuration the validated data source settings
     * @param employees employees in the snapshot
     * @return verified subjects keyed by directory employee ID
     */
    Map<String, String> resolve(IdentitySource identity, Map<String, String> configuration, List<Employee> employees);
}

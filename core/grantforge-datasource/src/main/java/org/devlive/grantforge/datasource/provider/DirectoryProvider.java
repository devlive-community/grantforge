// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.datasource.provider;

import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;

/**
 * A supplier of complete organization snapshots; contains no account or permission writes.
 */
public interface DirectoryProvider
{
    /**
     * Returns the stable provider type.
     *
     * @return the type
     */
    String type();

    /**
     * Validates and canonicalizes public settings; credentials stay separate.
     *
     * @param settings the provider settings
     * @return canonical settings
     */
    Map<String, String> validate(Map<String, String> settings);

    /**
     * Reads every page before exposing a snapshot.
     *
     * @param settings public provider settings
     * @param secret   the decrypted credential
     * @return a complete directory
     */
    Snapshot fetch(Map<String, String> settings, String secret);

    /**
     * Provider-neutral department identifiers, with parents preceding children.
     */
    record Department(String id, @Nullable String parentId, String name, int order)
    {
    }

    /**
     * Memberships within the configured directory scope.
     */
    record Employee(String id, @Nullable String email, List<String> departments)
    {
        /** Copies the membership list. */
        public Employee
        {
            departments = List.copyOf(departments);
        }
    }

    /**
     * A fully fetched snapshot and its authorization scope fingerprint.
     */
    record Snapshot(List<Department> departments, List<Employee> employees, String scope)
    {
        /** Copies the snapshot lists. */
        public Snapshot
        {
            departments = List.copyOf(departments);
            employees = List.copyOf(employees);
        }
    }
}

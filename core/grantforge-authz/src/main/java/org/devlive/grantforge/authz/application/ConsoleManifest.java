// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.authz.application;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The console's permission manifest: its modules, menus, pages and buttons and what each requires.
 *
 * @param resources the top-level resources
 */
public record ConsoleManifest(List<ManifestEntry> resources)
{
    /** Copies the resources; an omitted list becomes empty. */
    @SuppressWarnings("ConstantValue")
    public ConsoleManifest
    {
        resources = resources == null ? List.of() : List.copyOf(resources);
    }

    /**
     * Returns every entry, parents before their children.
     *
     * @return the entries depth first
     */
    public List<ManifestEntry> flatten()
    {
        List<ManifestEntry> all = new ArrayList<>();
        List<ManifestEntry> pending = new ArrayList<>(resources);
        Collections.reverse(pending);
        while (!pending.isEmpty()) {
            ManifestEntry entry = pending.remove(pending.size() - 1);
            all.add(entry);
            for (int i = entry.children().size() - 1; i >= 0; i--) {
                pending.add(entry.children().get(i));
            }
        }
        return all;
    }
}

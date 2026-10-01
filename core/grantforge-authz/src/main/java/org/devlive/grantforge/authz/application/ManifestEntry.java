// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.authz.application;

import org.devlive.grantforge.authz.domain.ResourceType;
import org.jspecify.annotations.Nullable;

import java.util.List;

import static java.util.Objects.requireNonNull;

/**
 * One resource of the console's permission manifest ({@code core/grantforge-web/src/permissions/manifest.json}).
 *
 * @param code the resource code, unique in the manifest
 * @param type what the resource stands for
 * @param name the English name, shown where the key has no translation
 * @param nameKey the console's message key of the name, or {@code null}
 * @param route the console path of a menu, page or tab, or {@code null}
 * @param apis permission codes whose API resources the resource requires; may be empty
 * @param requires codes of other manifest resources it requires; may be empty
 * @param children the resources below it; may be empty
 */
public record ManifestEntry(String code, ResourceType type, String name, @Nullable String nameKey, @Nullable String route,
        List<String> apis, List<String> requires, List<ManifestEntry> children)
{
    /** Checks the required values and copies the lists; omitted lists (JSON without them) become empty. */
    @SuppressWarnings("ConstantValue")
    public ManifestEntry
    {
        requireNonNull(code, "code");
        requireNonNull(type, "type of " + code);
        requireNonNull(name, "name of " + code);
        apis = apis == null ? List.of() : List.copyOf(apis);
        requires = requires == null ? List.of() : List.copyOf(requires);
        children = children == null ? List.of() : List.copyOf(children);
    }
}

// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.service.policy;

import org.jspecify.annotations.Nullable;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * What a policy covers and says. Access policies have all four item lists: allow items less their exceptions, deny
 * items less theirs. Masking and row filtering policies only have {@code allow}: their masking or filtering items.
 *
 * @param resources the values per resource level, from the top level down
 * @param allow the items that allow, or that mask or filter
 * @param allowExceptions whom the allow items do not apply to
 * @param deny the items that deny
 * @param denyExceptions whom the deny items do not apply to
 * @param validity the periods the policy applies in; always when empty
 */
public record PolicyDocument(Map<String, ResourceValues> resources, List<PolicyItemSpec> allow, List<PolicyItemSpec> allowExceptions,
        List<PolicyItemSpec> deny, List<PolicyItemSpec> denyExceptions, List<ValidityPeriod> validity)
{
    /** Copies the parts; parts missing from JSON are empty. */
    @SuppressWarnings("ConstantValue")
    public PolicyDocument
    {
        Map<String, ResourceValues> levels = new LinkedHashMap<>();
        if (resources != null) {
            resources.forEach((@Nullable String level, @Nullable ResourceValues values) -> {
                if (level != null && values != null) {
                    levels.put(level.strip(), values);
                }
            });
        }
        resources = Collections.unmodifiableMap(levels);
        allow = List.copyOf(Texts.list(allow));
        allowExceptions = List.copyOf(Texts.list(allowExceptions));
        deny = List.copyOf(Texts.list(deny));
        denyExceptions = List.copyOf(Texts.list(denyExceptions));
        validity = List.copyOf(Texts.list(validity));
    }

    /**
     * Describes a policy that only allows.
     *
     * @param resources the values per resource level
     * @param allow the allow items
     * @return the document
     */
    public static PolicyDocument allowing(Map<String, ResourceValues> resources, PolicyItemSpec... allow)
    {
        return new PolicyDocument(resources, List.of(allow), List.of(), List.of(), List.of(), List.of());
    }
}

// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.service.policy;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** Users alice and bob, the group ops and the role analyst, in every tenant. */
public final class FakePolicySubjects
        implements PolicySubjects
{
    private static final Map<SubjectKind, List<String>> KNOWN = Map.of(SubjectKind.USER, List.of("alice", "bob"),
            SubjectKind.GROUP, List.of("ops"), SubjectKind.ROLE, List.of("analyst"));

    @Override
    public Set<String> unknown(SubjectKind kind, Collection<String> names)
    {
        return names.stream().filter(name -> !KNOWN.getOrDefault(kind, List.of()).contains(name)).collect(Collectors.toSet());
    }

    @Override
    public List<String> suggest(SubjectKind kind, String text, int limit)
    {
        return KNOWN.getOrDefault(kind, List.of()).stream().filter(name -> name.contains(text)).limit(limit).toList();
    }
}

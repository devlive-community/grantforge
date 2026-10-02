// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.service.agent;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Role analyst held by alice and bob; group ops with carol; every other role or group empty. */
public final class FakeSnapshotSubjects
        implements SnapshotSubjects
{
    @Override
    public Map<String, List<String>> roleHolders(Collection<String> roleCodes, Instant now)
    {
        return roleCodes.stream().collect(Collectors.toMap(Function.identity(),
                code -> "analyst".equals(code) ? List.of("alice", "bob") : List.of()));
    }

    @Override
    public Map<String, List<String>> groupMembers(Collection<String> groupCodes)
    {
        return groupCodes.stream().collect(Collectors.toMap(Function.identity(), code -> "ops".equals(code) ? List.of("carol") : List.of()));
    }
}

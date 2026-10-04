// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.authz.data;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DataSubjectTest
{
    @Test
    void keepsItsOwnCopies()
    {
        List<String> groups = new ArrayList<>(List.of("ops"));
        DataSubject subject = new DataSubject(1, 2, "alice", List.of(3L), List.of("/3/"), groups, List.of());
        groups.add("hr");
        assertThat(subject.groupCodes()).containsExactly("ops");
        assertThat(subject.orgUnitPaths()).containsExactly("/3/");
    }
}

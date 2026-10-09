// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.plugin.api;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class BrowseEntryTest
{
    @Test
    void keepsWhatTheTargetSystemKnows()
    {
        BrowseEntry file = new BrowseEntry("notes.txt", "/user/notes.txt", false, "hdfs", "supergroup", "rw-r--r--", 12L,
                Instant.EPOCH);
        BrowseEntry bare = new BrowseEntry("alice", "/user/alice", true, null, null, null, null, null);

        assertThat(file.size()).isEqualTo(12L);
        assertThat(bare.directory()).isTrue();
        assertThat(bare.owner()).isNull();
    }
}

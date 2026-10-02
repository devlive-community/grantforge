// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.service.agent;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SignedSnapshotTest
{
    @Test
    void keepsItsOwnCopyOfTheBody()
    {
        byte[] body = {1, 2, 3};
        SignedSnapshot snapshot = new SignedSnapshot(body, "\"e\"", 4, "k", "s");
        body[0] = 9;
        snapshot.body()[1] = 9;
        assertThat(snapshot.body()).containsExactly(1, 2, 3);
        SignedSnapshot same = new SignedSnapshot(new byte[] {1, 2, 3}, "\"e\"", 4, "k", "s");
        assertThat(snapshot).isEqualTo(same).hasSameHashCodeAs(same).isNotEqualTo(new SignedSnapshot(new byte[] {1}, "\"e\"", 4, "k", "s"))
                .isNotEqualTo(new SignedSnapshot(new byte[] {1, 2, 3}, "\"e\"", 5, "k", "s"))
                .isNotEqualTo(new SignedSnapshot(new byte[] {1, 2, 3}, "\"f\"", 4, "k", "s"))
                .isNotEqualTo(new SignedSnapshot(new byte[] {1, 2, 3}, "\"e\"", 4, "j", "s"))
                .isNotEqualTo(new SignedSnapshot(new byte[] {1, 2, 3}, "\"e\"", 4, "k", "t"))
                .isNotEqualTo("snapshot");
        assertThat(snapshot.toString()).isEqualTo("SignedSnapshot[etag=\"e\", policyVersion=4, bytes=3]");
    }
}

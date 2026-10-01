// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.common.lang;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DigestsTest
{
    @Test
    void matchesKnownVectors()
    {
        assertThat(Digests.sha256Hex("")).isEqualTo("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855");
        assertThat(Digests.sha256Hex("abc")).isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
        assertThat(Digests.sha256("abc".getBytes(StandardCharsets.US_ASCII))).hasSize(32);
    }

    @Test
    @SuppressWarnings("NullAway") // deliberately violates the non-null contract to test the guards
    void rejectsNull()
    {
        assertThatThrownBy(() -> Digests.sha256(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> Digests.sha256Hex(null)).isInstanceOf(NullPointerException.class);
    }
}

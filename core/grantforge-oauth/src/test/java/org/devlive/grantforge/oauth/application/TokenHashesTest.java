// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.oauth.application;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TokenHashesTest
{
    @Test
    void hashesTokensAndKeepsPlaceholdersHashes()
    {
        String hash = TokenHashes.of("token");

        assertThat(hash).isEqualTo("3c469e9d6c5875d37a43f353d4f88e61fcf812c66eee3457465a40b0da4153e0");
        assertThat(TokenHashes.of(TokenHashes.placeholder(hash))).isEqualTo(hash);
        assertThat(TokenHashes.placeholder(hash)).startsWith(TokenHashes.UNKNOWN);
    }
}

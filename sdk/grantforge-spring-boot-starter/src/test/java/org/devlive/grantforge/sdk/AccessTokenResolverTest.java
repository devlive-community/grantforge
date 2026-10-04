// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.sdk;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AccessTokenResolverTest
{
    @Test
    void applicationsMayKeepTokensWhereTheyLike()
    {
        AccessTokenResolver fromSession = () -> "kept-in-session";

        assertThat(fromSession.currentToken()).isEqualTo("kept-in-session");
    }
}

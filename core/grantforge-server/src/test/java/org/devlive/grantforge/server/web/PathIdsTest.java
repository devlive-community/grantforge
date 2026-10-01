// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.web;

import org.devlive.grantforge.common.error.CommonErrorCode;
import org.devlive.grantforge.common.error.GrantForgeException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PathIdsTest
{
    @Test
    void parsesNumbersAndReportsAnythingElseAsNotFound()
    {
        assertThat(PathIds.parse("9007199254740993", "tenant")).isEqualTo(9_007_199_254_740_993L);
        assertThatThrownBy(() -> PathIds.parse("abc", "tenant")).isInstanceOfSatisfying(GrantForgeException.class,
                error -> assertThat(error.getErrorCode()).isEqualTo(CommonErrorCode.NOT_FOUND));
    }
}

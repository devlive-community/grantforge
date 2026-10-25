// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.clickhouse.agent;

import org.junit.jupiter.api.Test;

import java.net.ConnectException;

import static org.assertj.core.api.Assertions.assertThat;

class ClickHouseFailureTest
{
    @Test
    void carriesClickHousesCode()
    {
        ConnectException cause = new ConnectException("refused");
        ClickHouseFailure failure = new ClickHouseFailure(ClickHouseFailure.UNREACHABLE, "http://c1: refused", cause);
        assertThat(failure.code()).isEqualTo(-1);
        assertThat(failure).hasMessage("http://c1: refused").hasCause(cause);
        assertThat(new ClickHouseFailure(ClickHouseFailure.ACCESS_DENIED, "denied", null).code()).isEqualTo(497);
    }
}

// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.zookeeper;

import org.devlive.grantforge.plugin.api.LookupException;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

class TooManyChildrenExceptionTest
{
    @Test
    void reportsThePathChildCountAndServiceLimitAsALimitFailure()
    {
        TooManyChildrenException tooMany = new TooManyChildrenException("/brokers/ids", 501, 500);

        LookupException failure = ZooKeeperProvider.failure(tooMany);

        assertThat(failure.getReason()).isEqualTo(LookupException.Reason.LIMIT_EXCEEDED);
        assertThat(failure)
                .hasMessage("/brokers/ids has 501 children, more than the 500 this service reads at once")
                .hasCause(tooMany);
    }

    @Test
    void recognizesAWrappedLimitAndKeepsTheOuterMessagesFirstLine()
    {
        TooManyChildrenException tooMany = new TooManyChildrenException("/services", 101, 100);
        IOException wrapped = new IOException("cannot list /services\nrequest details", tooMany);

        LookupException failure = ZooKeeperProvider.failure(wrapped);

        assertThat(failure.getReason()).isEqualTo(LookupException.Reason.LIMIT_EXCEEDED);
        assertThat(failure).hasMessage("cannot list /services").hasCause(wrapped);
        assertThat(wrapped).hasCause(tooMany);
        assertThat(tooMany).hasMessage("/services has 101 children, more than the 100 this service reads at once");
    }
}

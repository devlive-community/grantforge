// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.hdfs.common;

import org.devlive.grantforge.agent.AgentDecision;
import org.devlive.grantforge.policy.engine.AccessRequest;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatNoException;

class HdfsMetricsTest
{
    @Test
    void absentHadoopMonitoringAcceptsAllCountersAndGauges()
    {
        assertThatNoException().isThrownBy(() -> {
            HdfsMetrics.NONE.callback();
            HdfsMetrics.NONE.superuserCallback();
            HdfsMetrics.NONE.nativeDeny();
            HdfsMetrics.NONE.failure();
            HdfsMetrics.NONE.missingSnapshot();
            HdfsMetrics.NONE.snapshot(7, 23, 4, false);
            HdfsMetrics.NONE.decision(AgentDecision.withoutSnapshot());
            HdfsMetrics.NONE.decision(HdfsAuthorizerTest.snapshot(7, "hdfs", null, "read")
                    .decide(AccessRequest.builder("alice", "read").resource("path", "/data/file").build()));
        });
    }
}

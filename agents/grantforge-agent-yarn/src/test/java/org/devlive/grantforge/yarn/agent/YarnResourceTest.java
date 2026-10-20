// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.yarn.agent;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class YarnResourceTest
{
    @Test
    void decidesAQueueAtEveryQueueOfItsPath()
    {
        YarnResource queue = YarnResource.queue("root.sales.etl", null);
        assertThat(queue.level()).isEqualTo("queue");
        assertThat(queue.display()).isEqualTo("root.sales.etl").isEqualTo(queue.toString());
        assertThat(queue.scopes()).containsExactly(Map.of("queue", "root"), Map.of("queue", "root.sales"), Map.of("queue", "root.sales.etl"));
        assertThat(YarnResource.queue("default", null).scopes()).containsExactly(Map.of("queue", "default"));
    }

    @Test
    void decidesAnApplicationByTheQueueAloneAndWithItsName()
    {
        YarnResource application = YarnResource.queue("root.hr", "payroll-oct");
        assertThat(application.level()).isEqualTo("application");
        assertThat(application.display()).isEqualTo("root.hr application payroll-oct");
        assertThat(application.scopes()).containsExactly(Map.of("queue", "root"), Map.of("queue", "root", "application", "payroll-oct"),
                Map.of("queue", "root.hr"), Map.of("queue", "root.hr", "application", "payroll-oct"));
    }

    @Test
    void namesTheCluster()
    {
        YarnResource cluster = YarnResource.cluster();
        assertThat(cluster.level()).isEqualTo("cluster");
        assertThat(cluster.display()).isEqualTo("cluster");
        assertThat(cluster.scopes()).containsExactly(Map.of("cluster", "*"));
    }
}

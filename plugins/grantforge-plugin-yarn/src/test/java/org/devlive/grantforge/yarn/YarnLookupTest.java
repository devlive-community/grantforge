// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.yarn;

import org.apache.hadoop.yarn.api.records.ApplicationReport;
import org.apache.hadoop.yarn.api.records.QueueInfo;
import org.apache.hadoop.yarn.client.api.YarnClient;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class YarnLookupTest
{
    private final YarnClient client = mock(YarnClient.class);

    private static QueueInfo queue(String name, QueueInfo... children)
    {
        QueueInfo queue = mock(QueueInfo.class);
        when(queue.getQueueName()).thenReturn(name);
        when(queue.getChildQueues()).thenReturn(children.length == 0 ? null : List.of(children));
        return queue;
    }

    private static ApplicationReport application(@Nullable String name, @Nullable String queue)
    {
        ApplicationReport report = mock(ApplicationReport.class);
        when(report.getName()).thenReturn(name);
        when(report.getQueue()).thenReturn(queue);
        return report;
    }

    @BeforeEach
    void capacityScheduler() throws Exception
    {
        List<QueueInfo> roots = List.of(queue("default"), queue("sales", queue("etl"), queue("adhoc")), queue("hr"));
        when(client.getRootQueueInfos()).thenReturn(roots);
        List<ApplicationReport> applications = List.of(application("etl-daily", "etl"), application("report", "adhoc"),
                application("payroll", "root.hr"), application("odd", null), application(null, "etl"));
        when(client.getApplications()).thenReturn(applications);
    }

    private static YarnLookup lookup(String resource, String typed, Map<String, List<String>> context)
    {
        return YarnLookup.of(resource, typed, context, 50);
    }

    @Test
    void looksUpTheLevelsItKnows()
    {
        assertThatIllegalArgumentException().isThrownBy(() -> lookup("cluster", "", Map.of())).withMessageEndingWith("not cluster");
        assertThat(lookup("queue", " root ", Map.of()).typed()).isEqualTo("root");
        assertThat(lookup("application", "", Map.of("queue", List.of(" root.sales ", "root.sales"))).queues()).containsExactly("root.sales");
        assertThat(lookup("application", "", Map.of("queue", List.of("root.a", "*"))).queues()).containsExactly("*");
        assertThat(lookup("application", "", Map.of("queue", List.of())).queues()).containsExactly("*");
        List<String> many = IntStream.rangeClosed(0, YarnLookup.MAX_QUEUES).mapToObj(index -> "root.q" + index).toList();
        assertThat(lookup("application", "", Map.of("queue", many)).queues()).containsExactly("*");
    }

    @Test
    void namesQueuesByTheirPath() throws Exception
    {
        assertThat(lookup("queue", "", Map.of()).run(client)).containsExactly("root", "root.default", "root.hr", "root.sales", "root.sales.adhoc",
                "root.sales.etl");
        assertThat(lookup("queue", "root.sales.", Map.of()).run(client)).containsExactly("root.sales.adhoc", "root.sales.etl");
        assertThat(lookup("queue", "ad", Map.of()).run(client)).containsExactly("root.sales.adhoc");
        assertThat(YarnLookup.of("queue", "", Map.of(), 2).run(client)).containsExactly("root", "root.default");
    }

    @Test
    void readsTheFairSchedulersWholePaths() throws Exception
    {
        List<QueueInfo> roots = List.of(queue("root", queue("root.users", queue("root.users.alice"))));
        when(client.getRootQueueInfos()).thenReturn(roots);
        assertThat(lookup("queue", "", Map.of()).run(client)).containsExactly("root", "root.users", "root.users.alice");
    }

    @Test
    void namesTheApplicationsOfTheQueuesAPolicyNames() throws Exception
    {
        assertThat(lookup("application", "", Map.of("queue", List.of("root.sales.*"))).run(client)).containsExactly("etl-daily", "report");
        assertThat(lookup("application", "", Map.of("queue", List.of("root.hr"))).run(client)).containsExactly("payroll");
        assertThat(lookup("application", "e", Map.of()).run(client)).containsExactly("etl-daily");
        assertThat(lookup("application", "", Map.of("queue", List.of("root.s?les.etl"))).run(client)).containsExactly("etl-daily");
    }

    @Test
    void findsAQueuesPathByItsNameOrPath()
    {
        Map<String, String> paths = YarnLookup.paths(List.of(queue("sales", queue("etl"))));
        assertThat(YarnLookup.path(paths, "etl")).isEqualTo("root.sales.etl");
        assertThat(YarnLookup.path(paths, "root.sales")).isEqualTo("root.sales");
        assertThat(YarnLookup.path(paths, "gone")).isEqualTo("gone");
        assertThat(YarnLookup.path(paths, null)).isEmpty();
        assertThat(YarnLookup.pattern("root.*").matcher("root.sales.etl").matches()).isTrue();
        assertThat(YarnLookup.pattern("root.s?").matcher("root.sx").matches()).isTrue();
        assertThat(YarnLookup.pattern("a.b").matcher("axb").matches()).isFalse();
    }
}

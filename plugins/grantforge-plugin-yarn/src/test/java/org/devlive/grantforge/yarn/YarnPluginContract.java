// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.yarn;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.security.UserGroupInformation;
import org.apache.hadoop.yarn.api.records.ApplicationSubmissionContext;
import org.apache.hadoop.yarn.api.records.ContainerLaunchContext;
import org.apache.hadoop.yarn.api.records.Resource;
import org.apache.hadoop.yarn.client.api.YarnClient;
import org.apache.hadoop.yarn.client.api.YarnClientApplication;
import org.devlive.grantforge.plugin.api.ConnectionResult;
import org.devlive.grantforge.plugin.api.LookupException;
import org.devlive.grantforge.plugin.api.LookupRequest;
import org.devlive.grantforge.plugin.api.ServiceConfig;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.Timeout;

import java.net.ServerSocket;
import java.security.PrivilegedExceptionAction;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The YARN plugin against a real ResourceManager of one Hadoop release: the connection test, lookups of queues and of
 * the names of applications in them, failing over to the next ResourceManager, and one that cannot be reached.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Timeout(value = 10, unit = TimeUnit.MINUTES)
abstract class YarnPluginContract
{
    private final YarnProvider provider = new YarnProvider();
    private @Nullable YarnCluster cluster;

    /**
     * The release under test.
     *
     * @return such as {@code 3.4.3}
     */
    abstract String version();

    @BeforeAll
    void start() throws Exception
    {
        YarnCluster started = new YarnCluster(version());
        cluster = started;
        started.start();
        submit(started.address(), "alice", "etl", "etl-daily");
        submit(started.address(), "alice", "adhoc", "report-weekly");
        submit(started.address(), "bob", "hr", "payroll");
    }

    @AfterAll
    void stop()
    {
        if (cluster != null) {
            cluster.stop();
        }
    }

    /** Submits an application that waits, with no NodeManager to run it. */
    static void submit(String address, String user, String queue, String name) throws Exception
    {
        Configuration configuration = new Configuration();
        configuration.set(YarnSettings.RESOURCE_MANAGER, address);
        UserGroupInformation.createRemoteUser(user).doAs((PrivilegedExceptionAction<Void>) () -> {
            try (YarnClient client = YarnClient.createYarnClient()) {
                client.init(configuration);
                client.start();
                YarnClientApplication application = client.createApplication();
                ApplicationSubmissionContext context = application.getApplicationSubmissionContext();
                context.setApplicationName(name);
                context.setQueue(queue);
                context.setResource(Resource.newInstance(256, 1));
                context.setAMContainerSpec(ContainerLaunchContext.newInstance(Map.of(), Map.of(), List.of("sleep 600"), null, null, null));
                client.submitApplication(context);
            }
            return null;
        });
    }

    private ServiceConfig service(String... values)
    {
        Map<String, String> map = new HashMap<>(Map.of(YarnSettings.RESOURCE_MANAGER, requireNonNull(cluster, "cluster").address(), YarnSettings.USER,
                "grantforge", YarnSettings.TIMEOUT, "15000"));
        for (int index = 0; index < values.length; index += 2) {
            map.put(values[index], values[index + 1]);
        }
        return new ServiceConfig("compute", map);
    }

    private List<String> lookup(String resource, String typed, Map<String, List<String>> context)
    {
        return provider.lookup(new LookupRequest(service(), resource, typed, context, 50));
    }

    private static int closedPort() throws Exception
    {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    @Test
    void connectsAndDescribesTheCluster()
    {
        ConnectionResult result = provider.testConnection(service());
        assertThat(result.status()).as(String.valueOf(result.message())).isEqualTo(ConnectionResult.Status.SUCCEEDED);
        assertThat(result.message()).isEqualTo("0 NodeManagers, 6 queues");
    }

    @Test
    void looksUpQueuesByPathOrName()
    {
        assertThat(lookup("queue", "", Map.of())).containsExactly("root", "root.default", "root.hr", "root.sales", "root.sales.adhoc",
                "root.sales.etl");
        assertThat(lookup("queue", "root.sales.", Map.of())).containsExactly("root.sales.adhoc", "root.sales.etl");
        assertThat(lookup("queue", "etl", Map.of())).as("by its own name").containsExactly("root.sales.etl");
        assertThat(lookup("cluster", "", Map.of())).containsExactly("*");
    }

    @Test
    void looksUpTheApplicationsOfTheQueuesAPolicyNames()
    {
        assertThat(lookup("application", "", Map.of("queue", List.of("root.sales.*")))).containsExactly("etl-daily", "report-weekly");
        assertThat(lookup("application", "etl", Map.of())).containsExactly("etl-daily");
        assertThat(lookup("application", "", Map.of("queue", List.of("root.hr")))).containsExactly("payroll");
        assertThat(lookup("application", "", Map.of("queue", List.of("root.default")))).isEmpty();
    }

    @Test
    void failsOverToTheNextResourceManager() throws Exception
    {
        ServiceConfig config = service(YarnSettings.RESOURCE_MANAGER, "localhost:" + closedPort() + "," + requireNonNull(cluster, "cluster").address());
        ConnectionResult result = provider.testConnection(config);
        assertThat(result.status()).as(String.valueOf(result.message())).isEqualTo(ConnectionResult.Status.SUCCEEDED);
    }

    @Test
    void namesAResourceManagerThatCannotBeReached() throws Exception
    {
        ServiceConfig config = service(YarnSettings.RESOURCE_MANAGER, "localhost:" + closedPort(), YarnSettings.TIMEOUT, "3000");
        assertThatThrownBy(() -> provider.lookup(new LookupRequest(config, "queue", "", Map.of(), 10))).isInstanceOfSatisfying(LookupException.class,
                failed -> assertThat(failed.getReason()).as(String.valueOf(failed.getMessage())).isEqualTo(LookupException.Reason.UNREACHABLE));
        assertThat(provider.testConnection(config).status()).isEqualTo(ConnectionResult.Status.FAILED);
    }
}

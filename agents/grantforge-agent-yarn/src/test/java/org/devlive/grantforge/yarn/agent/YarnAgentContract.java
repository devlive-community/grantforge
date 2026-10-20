// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.yarn.agent;

import com.fasterxml.jackson.databind.JsonNode;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.security.UserGroupInformation;
import org.apache.hadoop.yarn.api.records.ApplicationId;
import org.apache.hadoop.yarn.api.records.ApplicationSubmissionContext;
import org.apache.hadoop.yarn.api.records.ContainerLaunchContext;
import org.apache.hadoop.yarn.api.records.Resource;
import org.apache.hadoop.yarn.client.api.YarnClient;
import org.apache.hadoop.yarn.client.api.YarnClientApplication;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.Timeout;
import org.testcontainers.Testcontainers;
import org.testcontainers.containers.Container;
import org.testcontainers.utility.MountableFile;

import java.lang.reflect.UndeclaredThrowableException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.PrivilegedExceptionAction;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The shaded agent jar in a real ResourceManager of one Hadoop release, against a GrantForge server of the tests' own:
 * who may submit which applications to which queue at every queue of its path, who may kill other users' applications,
 * who may administer the cluster, policy items that hold for some client addresses only, access events, and a policy
 * change without a restart.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Timeout(value = 10, unit = TimeUnit.MINUTES)
abstract class YarnAgentContract
{
    private @Nullable AgentServer server;
    private @Nullable YarnCluster cluster;

    /**
     * The release under test.
     *
     * @return such as {@code 3.4.3}
     */
    abstract String version();

    private static YarnSnapshots policies()
    {
        return new YarnSnapshots()
                .allow(List.of("@analysts"), List.of("submit_app"), "queue", "root.sales")
                .deny(List.of("alice"), List.of("submit_app"), "queue", "root.sales.adhoc")
                .allow(List.of("bob"), List.of("submit_app"), "queue", "root.hr", "application", "payroll-*")
                .allow(List.of("carol"), List.of("admin_queue"), "queue", "root.sales")
                .allow(List.of("dave"), List.of("admin"), "cluster", "*")
                .allowFrom(List.of("0.0.0.0/0"), List.of("erin"), List.of("submit_app"), "queue", "root.default")
                .allowFrom(List.of("203.0.113.0/24"), List.of("frank"), List.of("submit_app"), "queue", "root.default")
                .group("analysts", "alice");
    }

    @BeforeAll
    void start() throws Exception
    {
        AgentServer started = new AgentServer(policies());
        server = started;
        Testcontainers.exposeHostPorts(started.port());
        // YARN's own administrators may do anything to every application: only the ResourceManager's user is one, so that
        // GrantForge decides who administers a queue.
        YarnCluster resourceManager = new YarnCluster(version()).yarn("yarn.acl.enable", "true").yarn("yarn.admin.acl", "hadoop")
                .yarn("yarn.authorization-provider", GrantForgeYarnAuthorizer.class.getName())
                .yarn("grantforge.server.url", "http://host.testcontainers.internal:" + started.port() + "/")
                .yarn("grantforge.token.file", "/opt/hadoop/etc/hadoop/grantforge.token").yarn("grantforge.instance", "resourcemanager")
                .yarn("grantforge.cache.dir", "/tmp/grantforge").yarn("grantforge.refresh.interval.ms", "1000")
                .yarn("grantforge.audit.flush.interval.ms", "200").yarn("grantforge.audit.fold.window.ms", "0")
                .file("/opt/hadoop/etc/hadoop/grantforge.token", AgentServer.TOKEN.getBytes(StandardCharsets.UTF_8));
        resourceManager.container().withCopyFileToContainer(MountableFile.forHostPath(Path.of(requireNonNull(
                System.getProperty("grantforge.yarn.agent.jar"), "grantforge.yarn.agent.jar"))), "/opt/hadoop/share/hadoop/yarn/lib/grantforge-agent.jar");
        cluster = resourceManager;
        resourceManager.start();
        eventually("the agent takes the policies", () -> submits("alice", "etl", "warm-up"));
    }

    @AfterAll
    void stop()
    {
        if (cluster != null) {
            cluster.stop();
        }
        if (server != null) {
            server.close();
        }
    }

    private <T> T as(String user, YarnWork<T> work) throws Exception
    {
        Configuration configuration = new Configuration();
        configuration.set("yarn.resourcemanager.address", requireNonNull(cluster, "cluster").address());
        configuration.setInt("yarn.resourcemanager.connect.max-wait.ms", 10_000);
        try {
            return UserGroupInformation.createRemoteUser(user).doAs((PrivilegedExceptionAction<T>) () -> {
                try (YarnClient client = YarnClient.createYarnClient()) {
                    client.init(configuration);
                    client.start();
                    return work.run(client);
                }
            });
        }
        catch (UndeclaredThrowableException wrapped) {
            // YARN's refusals are YarnExceptions, which doAs wraps.
            throw (Exception) wrapped.getCause();
        }
    }

    @FunctionalInterface
    private interface YarnWork<T>
    {
        T run(YarnClient client) throws Exception;
    }

    private ApplicationId submit(String user, String queue, String name) throws Exception
    {
        return as(user, client -> {
            YarnClientApplication application = client.createApplication();
            ApplicationSubmissionContext context = application.getApplicationSubmissionContext();
            context.setApplicationName(name);
            context.setQueue(queue);
            context.setResource(Resource.newInstance(256, 1));
            context.setAMContainerSpec(ContainerLaunchContext.newInstance(Map.of(), Map.of(), List.of("sleep 600"), null, null, null));
            return client.submitApplication(context);
        });
    }

    private void kill(String user, ApplicationId application) throws Exception
    {
        as(user, client -> {
            client.killApplication(application);
            return application;
        });
    }

    private boolean submits(String user, String queue, String name)
    {
        try {
            submit(user, queue, name);
            return true;
        }
        catch (Exception refused) {
            return false;
        }
    }

    private static void eventually(String what, BooleanSupplier condition) throws InterruptedException
    {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(90);
        while (!condition.getAsBoolean()) {
            if (System.nanoTime() > deadline) {
                throw new AssertionError("timed out waiting until " + what);
            }
            Thread.sleep(500);
        }
    }

    private Container.ExecResult admin(String user, String... arguments) throws Exception
    {
        String[] command = new String[arguments.length + 3];
        command[0] = "env";
        command[1] = "HADOOP_USER_NAME=" + user;
        command[2] = "/opt/hadoop/bin/yarn";
        System.arraycopy(arguments, 0, command, 3, arguments.length);
        return requireNonNull(cluster, "cluster").container().execInContainer(command);
    }

    @Test
    void submitsAtEveryQueueOfThePath() throws Exception
    {
        submit("alice", "etl", "etl-daily");
        assertThatThrownBy(() -> submit("alice", "adhoc", "report")).as("a denial below the parent's allowance")
                .hasMessageContaining("alice");
        assertThatThrownBy(() -> submit("alice", "hr", "payroll-oct")).hasMessageContaining("alice");
        assertThatThrownBy(() -> submit("mallory", "etl", "x")).hasMessageContaining("mallory");
    }

    @Test
    void submitsOnlyTheApplicationsThePolicyNames() throws Exception
    {
        submit("bob", "hr", "payroll-oct");
        assertThatThrownBy(() -> submit("bob", "hr", "bonus")).hasMessageContaining("bob");
    }

    @Test
    void killsOtherUsersApplicationsAsTheQueuesAdministrator() throws Exception
    {
        ApplicationId application = submit("alice", "etl", "etl-to-kill");
        assertThatThrownBy(() -> kill("mallory", application)).hasMessageContaining("mallory");
        kill("carol", application);
        submit("carol", "adhoc", "carol-adhoc");
    }

    @Test
    void administersTheClusterUnderPolicy() throws Exception
    {
        assertThat(admin("dave", "rmadmin", "-refreshQueues").getExitCode()).isZero();
        Container.ExecResult refused = admin("mallory", "rmadmin", "-refreshQueues");
        assertThat(refused.getExitCode()).isNotZero();
        assertThat(refused.getStderr()).contains("mallory");
    }

    @Test
    void holdsForTheClientAddressesAPolicyNames() throws Exception
    {
        submit("erin", "default", "anywhere");
        assertThatThrownBy(() -> submit("frank", "default", "elsewhere")).hasMessageContaining("frank");
    }

    @Test
    void appliesAPolicyChangeWithoutARestart() throws Exception
    {
        AgentServer running = requireNonNull(server, "server");
        assertThat(submits("grace", "hr", "audit")).isFalse();
        running.publish(policies().allow(List.of("grace"), List.of("submit_app"), "queue", "root.hr"));
        eventually("grace may submit to hr", () -> submits("grace", "hr", "audit"));
        running.publish(policies());
        eventually("grace may no longer", () -> !submits("grace", "hr", "audit"));
    }

    @Test
    void reportsAccessesToTheServer() throws Exception
    {
        assertThat(submits("alice", "adhoc", "reported")).isFalse();
        AgentServer running = requireNonNull(server, "server");
        eventually("the refusal arrives", () -> running.events().stream().anyMatch(YarnAgentContract::refused));
        JsonNode event = running.events().stream().filter(YarnAgentContract::refused).findFirst().orElseThrow();
        assertThat(event.path("user").asText()).isEqualTo("alice");
        assertThat(event.path("accessType").asText()).isEqualTo("submit_app");
        assertThat(event.path("action").asText()).isEqualTo("submit application");
        assertThat(event.path("enforcer").asText()).isEqualTo("GRANTFORGE");
        assertThat(event.path("clientIp").asText()).isNotEmpty();
        assertThat(event.path("request").asText()).startsWith("application application_").endsWith(" reported");
    }

    private static boolean refused(JsonNode event)
    {
        return "root.sales.adhoc application reported".equals(event.path("resource").asText()) && "DENIED".equals(event.path("outcome").asText());
    }
}

// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.yarn.agent;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.security.UserGroupInformation;
import org.apache.hadoop.security.authorize.AccessControlList;
import org.apache.hadoop.yarn.exceptions.YarnRuntimeException;
import org.apache.hadoop.yarn.security.AccessRequest;
import org.apache.hadoop.yarn.security.AccessType;
import org.apache.hadoop.yarn.security.Permission;
import org.apache.hadoop.yarn.security.PrivilegedEntity;
import org.devlive.grantforge.agent.EventFields;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GrantForgeYarnAuthorizerTest
{
    @TempDir
    Path temporary;

    private final TestRuntime test = new TestRuntime(new YarnSnapshots()
            .allow(List.of("@analysts"), List.of("submit_app"), "queue", "root.sales")
            .deny(List.of("alice"), List.of("submit_app"), "queue", "root.sales.adhoc")
            .allow(List.of("bob"), List.of("submit_app"), "queue", "root.hr", "application", "payroll-*")
            .allow(List.of("carol"), List.of("admin_queue"), "queue", "root.sales")
            .allow(List.of("dave"), List.of("admin"), "cluster", "*")
            .group("analysts", "alice").snapshot());
    private final GrantForgeYarnAuthorizer authorizer = new GrantForgeYarnAuthorizer(test.runtime(), "yarn");

    private static AccessRequest request(String user, AccessType type, @Nullable String queue, @Nullable String application,
            @Nullable String remote, @Nullable List<String> forwarded)
    {
        PrivilegedEntity entity = queue == null ? null : new PrivilegedEntity(PrivilegedEntity.EntityType.QUEUE, queue);
        return new AccessRequest(entity, UserGroupInformation.createRemoteUser(user), type, "application_1_0001", application, remote, forwarded);
    }

    private static AccessRequest submit(String user, String queue, String application)
    {
        return request(user, AccessType.SUBMIT_APP, queue, application, "10.0.0.7", null);
    }

    @Test
    void decidesSubmittingAtEveryQueueOfThePath()
    {
        assertThat(authorizer.checkPermission(submit("alice", "root.sales.etl", "etl-daily"))).isTrue();
        assertThat(authorizer.checkPermission(submit("alice", "root.sales.adhoc", "report"))).isFalse();
        assertThat(authorizer.checkPermission(submit("alice", "root.hr", "payroll-oct"))).isFalse();
        assertThat(authorizer.checkPermission(submit("bob", "root.hr", "payroll-oct"))).isTrue();
        assertThat(authorizer.checkPermission(submit("bob", "root.hr", "bonus"))).isFalse();
        assertThat(authorizer.checkPermission(submit("bob", "root.hr", " "))).as("no name, no application grant").isFalse();
    }

    @Test
    void decidesAdministeringQueuesAndTheCluster()
    {
        assertThat(authorizer.checkPermission(request("carol", AccessType.ADMINISTER_QUEUE, "root.sales.etl", null, "10.0.0.7", null))).isTrue();
        assertThat(authorizer.checkPermission(request("alice", AccessType.ADMINISTER_QUEUE, "root.sales.etl", null, "10.0.0.7", null))).isFalse();
        assertThat(authorizer.checkPermission(submit("carol", "root.sales.adhoc", "x"))).as("a queue's administrator submits to it").isTrue();
        assertThat(authorizer.isAdmin(UserGroupInformation.createRemoteUser("dave"))).isTrue();
        assertThat(authorizer.isAdmin(UserGroupInformation.createRemoteUser("alice"))).isFalse();
    }

    @Test
    void leavesTheResourceManagersOwnUserUnchecked()
    {
        assertThat(authorizer.checkPermission(submit("yarn", "root.hr", "anything"))).isTrue();
        assertThat(authorizer.isAdmin(UserGroupInformation.createRemoteUser("yarn"))).isTrue();
        assertThat(test.events()).isEmpty();
    }

    @Test
    void refusesWhatItDoesNotDecide()
    {
        assertThat(authorizer.checkPermission(request("alice", AccessType.APPLICATION_MAX_PRIORITY, "root.sales", null, null, null))).isFalse();
        assertThat(authorizer.checkPermission(request("alice", AccessType.SUBMIT_APP, null, "x", null, null))).isFalse();
        assertThat(authorizer.checkPermission(request("alice", AccessType.SUBMIT_APP, "", "x", null, null))).isFalse();
        assertThat(authorizer.checkPermission(new AccessRequest(null, null, AccessType.SUBMIT_APP, null, null, null, null))).isFalse();
    }

    @Test
    void reportsEveryCheckWithItsClientAndApplication()
    {
        authorizer.checkPermission(request("alice", AccessType.SUBMIT_APP, "root.sales.adhoc", "report", "10.0.0.7", List.of("203.0.113.9")));
        authorizer.isAdmin(UserGroupInformation.createRemoteUser("mallory"));
        Map<String, @Nullable Object> submitted = EventFields.of(test.events().get(0));
        assertThat(submitted).containsEntry("user", "alice").containsEntry("resource", "root.sales.adhoc application report")
                .containsEntry("accessType", "submit_app").containsEntry("outcome", "DENIED").containsEntry("clientIp", "203.0.113.9")
                .containsEntry("action", "submit application").containsEntry("request", "application application_1_0001 report")
                .containsEntry("resourceType", "application").containsEntry("policyId", "2");
        assertThat(EventFields.of(test.events().get(1))).containsEntry("resource", "cluster").containsEntry("action", "administer cluster")
                .containsEntry("clientIp", null);
        authorizer.checkPermission(request("carol", AccessType.ADMINISTER_QUEUE, "root.sales", null, null, null));
        assertThat(EventFields.of(test.events().get(2))).containsEntry("action", "administer queue").containsEntry("resource", "root.sales")
                .containsEntry("request", "application application_1_0001");
        authorizer.checkPermission(new AccessRequest(new PrivilegedEntity(PrivilegedEntity.EntityType.QUEUE, "root.sales.etl"),
                UserGroupInformation.createRemoteUser("alice"), AccessType.SUBMIT_APP, null, "etl", null, null));
        assertThat(EventFields.of(test.events().get(3))).containsEntry("request", null);
    }

    @Test
    void namesWhyPoliciesRefuse()
    {
        Policies policies = test.runtime().policies();
        assertThat(GrantForgeYarnAuthorizer.why(policies.decide(new Caller("alice", List.of("analysts"), null),
                YarnResource.queue("root.sales.adhoc", null), "submit_app"))).isEqualTo("GrantForge policy 2 denies it");
        assertThat(GrantForgeYarnAuthorizer.why(policies.decide(new Caller("mallory", List.of(), null), YarnResource.cluster(), "admin")))
                .isEqualTo("no GrantForge policy allows it");
    }

    @Test
    void leavesYarnUncheckedWhileTheServiceIsNotInUse()
    {
        test.snapshot(new YarnSnapshots().disabled().snapshot());
        assertThat(authorizer.checkPermission(submit("mallory", "root.hr", "x"))).isTrue();
        assertThat(authorizer.isAdmin(UserGroupInformation.createRemoteUser("mallory"))).isTrue();
    }

    @Test
    void refusesEverythingBeforeItStarts()
    {
        GrantForgeYarnAuthorizer unstarted = new GrantForgeYarnAuthorizer();
        assertThat(unstarted.checkPermission(submit("alice", "root.sales", "x"))).isFalse();
        assertThat(unstarted.isAdmin(UserGroupInformation.createRemoteUser("alice"))).isFalse();
        assertThat(unstarted).hasToString("GrantForge");
    }

    @Test
    void takesNoAclsOfTheConfiguration()
    {
        PrivilegedEntity queue = new PrivilegedEntity(PrivilegedEntity.EntityType.QUEUE, "root.sales");
        UserGroupInformation rm = UserGroupInformation.createRemoteUser("yarn");
        authorizer.setPermission(List.of(new Permission(queue, Map.of(AccessType.SUBMIT_APP, new AccessControlList("*")))), rm);
        authorizer.setPermission(List.of(new Permission(queue, Map.of())), rm);
        authorizer.setPermission(List.of(), rm);
        authorizer.setAdmins(new AccessControlList("*"), rm);
        assertThat(authorizer.isAdmin(UserGroupInformation.createRemoteUser("mallory"))).isFalse();
    }

    @Test
    void startsFromTheResourceManagersSettingsOnce() throws Exception
    {
        Configuration configuration = new Configuration(false);
        TestRuntime.settings(temporary).forEach(configuration::set);
        configuration.set("grantforge.server.url", "http://127.0.0.1:1/");
        configuration.set("yarn.acl.enable", "true");
        GrantForgeYarnAuthorizer started = new GrantForgeYarnAuthorizer();
        started.init(configuration);
        started.init(configuration);
        assertThat(started.checkPermission(submit(UserGroupInformation.getLoginUser().getShortUserName(), "root", "x"))).isTrue();
        assertThat(started.checkPermission(submit("someone-else", "root", "x"))).as("no policies yet").isFalse();
        GrantForgeYarnAuthorizer broken = new GrantForgeYarnAuthorizer();
        assertThatThrownBy(() -> broken.init(new Configuration(false))).isInstanceOf(YarnRuntimeException.class)
                .hasMessageContaining("grantforge.token.file");
    }

    @Test
    void readsTheVersionTheBuildWrote() throws Exception
    {
        assertThat(GrantForgeYarnAuthorizer.version()).isNotBlank().doesNotContain("${");
        assertThat(GrantForgeYarnAuthorizer.version(stream("grantforge.version = 2026.2.0 \n"))).isEqualTo("2026.2.0");
        assertThatThrownBy(() -> GrantForgeYarnAuthorizer.version(null)).isInstanceOf(IOException.class);
        assertThatThrownBy(() -> GrantForgeYarnAuthorizer.version(stream("grantforge.version = \n"))).isInstanceOf(IOException.class);
        assertThatThrownBy(() -> GrantForgeYarnAuthorizer.version(stream("other=1\n"))).isInstanceOf(IOException.class);
    }

    private static InputStream stream(String text)
    {
        return new ByteArrayInputStream(text.getBytes(StandardCharsets.ISO_8859_1));
    }
}

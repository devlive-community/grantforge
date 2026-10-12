// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.zookeeper.agent;

import org.devlive.grantforge.agent.AgentDecision;
import org.devlive.grantforge.policy.engine.AccessRequest;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ZooKeeperAuthorizerTest
{
    private static AgentDecision allowed()
    {
        AgentDecision decision = mock(AgentDecision.class);
        when(decision.allowed()).thenReturn(true);
        return decision;
    }

    @Test
    void mapsAllPermissionsAndRejectsUnknownBits()
    {
        assertThat(ZooKeeperAuthorizer.accessTypes(31)).containsExactly("read", "write", "create", "delete", "admin");
        for (int invalid : new int[] {0, -1, 32, 33}) {
            assertThat(ZooKeeperAuthorizer.accessTypes(invalid)).isEmpty();
        }
    }

    @Test
    void mapsShortAndFullPrincipals()
    {
        ZooKeeperAuthorizer shortNames = new ZooKeeperAuthorizer(() -> request -> allowed(), true);
        assertThat(shortNames.user("alice/host@REALM")).isEqualTo("alice");
        assertThat(shortNames.user("alice@REALM/host")).isEqualTo("alice");
        assertThat(shortNames.user("alice")).isEqualTo("alice");
        assertThat(new ZooKeeperAuthorizer(() -> request -> allowed(), false).user("alice/host@REALM"))
                .isEqualTo("alice/host@REALM");
    }

    @Test
    void preservesTheTargetPathAndClientAddress()
    {
        List<AccessRequest> requests = new ArrayList<>();
        ZooKeeperAuthorizer authorizer = new ZooKeeperAuthorizer(() -> request -> {
            requests.add(request);
            return allowed();
        }, true);
        assertThat(authorizer.authorize("alice/host@REALM", "/chroot/parent/new", ZooKeeperAuthorizer.CREATE, "127.0.0.1")).isTrue();
        assertThat(authorizer.authorize("alice", "/chroot/parent/new", ZooKeeperAuthorizer.DELETE, null)).isTrue();
        assertThat(requests).extracting(AccessRequest::accessType).containsExactly("create", "delete");
        assertThat(requests.get(0).resource()).containsEntry("path", "/chroot/parent/new");
        assertThat(requests.get(0).user()).isEqualTo("alice");
        assertThat(requests.get(0).context()).containsEntry("clientAddress", "127.0.0.1");
        assertThat(requests.get(1).context()).isEmpty();
    }

    @Test
    void compositeChecksUseOneSnapshotAndAnyPermissionCanAllow()
    {
        AtomicInteger sessions = new AtomicInteger();
        List<String> checked = new ArrayList<>();
        ZooKeeperAuthorizer authorizer = new ZooKeeperAuthorizer(() -> {
            sessions.incrementAndGet();
            return request -> {
                checked.add(request.accessType());
                return "admin".equals(request.accessType()) ? allowed() : AgentDecision.withoutSnapshot();
            };
        }, false);
        assertThat(authorizer.authorize("alice", "/node", ZooKeeperAuthorizer.READ | ZooKeeperAuthorizer.ADMIN, null)).isTrue();
        assertThat(checked).containsExactly("read", "admin");
        assertThat(sessions).hasValue(1);
        assertThat(authorizer.authorize("alice", "/node", ZooKeeperAuthorizer.WRITE, null)).isFalse();
    }

    @Test
    void refusesMissingOrBrokenPolicyState()
    {
        ZooKeeperAuthorizer unavailable = new ZooKeeperAuthorizer(() -> {
            throw new IllegalStateException("stopped");
        }, true);
        assertThat(unavailable.authorize("alice", "/node", 1, null)).isFalse();
        assertThat(new ZooKeeperAuthorizer(() -> null, true).authorize("alice", "/node", 1, null)).isFalse();
        assertThat(new ZooKeeperAuthorizer(() -> request -> null, true).authorize("alice", "/node", 1, null)).isFalse();
        assertThat(new ZooKeeperAuthorizer(() -> request -> {
            throw new IllegalArgumentException("broken");
        }, true).authorize("alice", "/node", 1, null)).isFalse();
        assertThat(unavailable.authorize("alice", "/node", 0, null)).isFalse();
    }
}

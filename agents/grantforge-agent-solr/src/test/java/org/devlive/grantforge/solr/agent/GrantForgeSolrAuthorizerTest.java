// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.solr.agent;

import org.apache.solr.security.AuthorizationContext;
import org.apache.solr.security.AuthorizationResponse;
import org.apache.solr.security.PermissionNameProvider;
import org.apache.solr.security.VerifiedUserRoles;
import org.devlive.grantforge.agent.AccessEvent;
import org.devlive.grantforge.agent.EventFields;
import org.devlive.grantforge.agent.Snapshot;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.Principal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GrantForgeSolrAuthorizerTest
{
    @TempDir
    Path temporary;

    private final TestRuntime test = new TestRuntime(new SolrSnapshots()
            .allow(List.of("alice"), List.of("query"), "collection", "sales")
            .deny(List.of("alice"), List.of("query"), "collection", "sales", "path", "/export")
            .allow(List.of("bob"), List.of("update", "schema_edit", "config_read"), "collection", "sales")
            .allow(List.of("carol"), List.of("admin_collection"), "collection", "team-*")
            .allow(List.of("dave"), List.of("metrics", "health"), "cluster", "*")
            .allow(List.of("@readers"), List.of("admin_read"), "cluster", "*")
            .allow(List.of("root"), List.of("admin"), "cluster", "*")
            .allowFrom(List.of("10.0.0.0/8"), List.of("erin"), List.of("query"), "collection", "hr")
            .snapshot());
    private final GrantForgeSolrAuthorizer authorizer = new GrantForgeSolrAuthorizer(test.runtime(), AddressRange.parse(List.of("192.0.2.0/24")));

    private int status(RequestContext context)
    {
        return authorizer.authorize(context).statusCode;
    }

    private static RequestContext query(String user, String collection, String path)
    {
        return RequestContext.of(user).collections(collection).resource(path).permission(PermissionNameProvider.Name.READ_PERM);
    }

    private static RequestContext admin(String user, PermissionNameProvider.@Nullable Name name, String... params)
    {
        RequestContext context = RequestContext.of(user).type(AuthorizationContext.RequestType.ADMIN).resource("/admin/collections").permission(name);
        for (int index = 0; index < params.length; index += 2) {
            context.param(params[index], params[index + 1]);
        }
        return context;
    }

    private Map<String, @Nullable Object> lastEvent()
    {
        List<AccessEvent> events = test.events();
        assertThat(events).isNotEmpty();
        return EventFields.of(events.get(events.size() - 1));
    }

    @Test
    void queriesACollectionButNotThroughADeniedPath()
    {
        assertThat(status(query("alice", "sales", "/select"))).isEqualTo(200);
        assertThat(lastEvent()).containsEntry("resource", "collection sales path /select").containsEntry("action", "read")
                .containsEntry("accessType", "query").containsEntry("clientIp", "10.0.0.7").containsEntry("resourceType", "path");
        AuthorizationResponse export = authorizer.authorize(query("alice", "sales", "/export"));
        assertThat(export.statusCode).isEqualTo(403);
        assertThat(export.getMessage()).contains("GrantForge policy 2 denies it", "alice", "collection sales path /export");
        assertThat(status(query("alice", "hr", "/select"))).isEqualTo(403);
        assertThat(status(query("alice", "sales", "/select").collections("hr"))).as("every collection of the request").isEqualTo(403);
        assertThat(status(query("alice", "sales", "/x").resource(null))).isEqualTo(200);
    }

    @Test
    void decidesUpdatesSchemasAndConfigs()
    {
        RequestContext update = RequestContext.of("bob").collections("sales").resource("/update").permission(PermissionNameProvider.Name.UPDATE_PERM);
        assertThat(status(update)).isEqualTo(200);
        assertThat(status(RequestContext.of("bob").collections("sales").resource("/schema").permission(PermissionNameProvider.Name.SCHEMA_EDIT_PERM)))
                .isEqualTo(200);
        assertThat(status(RequestContext.of("bob").collections("sales").resource("/schema").permission(PermissionNameProvider.Name.SCHEMA_READ_PERM)))
                .isEqualTo(200);
        assertThat(status(RequestContext.of("bob").collections("sales").resource("/config").permission(PermissionNameProvider.Name.CONFIG_READ_PERM)))
                .isEqualTo(200);
        assertThat(status(RequestContext.of("bob").collections("sales").resource("/config").permission(PermissionNameProvider.Name.CONFIG_EDIT_PERM)))
                .isEqualTo(403);
        assertThat(status(query("bob", "sales", "/select"))).isEqualTo(403);
    }

    @Test
    void decidesHandlersThatNameNoPermissionByTheRequestsType()
    {
        assertThat(status(RequestContext.of("alice").collections("sales").handler("custom").type(AuthorizationContext.RequestType.READ))).isEqualTo(200);
        assertThat(status(RequestContext.of("bob").collections("sales").permission(PermissionNameProvider.Name.ALL)
                .type(AuthorizationContext.RequestType.WRITE))).isEqualTo(200);
        assertThat(lastEvent()).containsEntry("action", "write");
        assertThat(status(RequestContext.of("bob").collections("sales").handler((PermissionNameProvider) request -> {
            throw new IllegalStateException("no name");
        }).type(AuthorizationContext.RequestType.UNKNOWN))).as("what Solr does not name needs the cluster's admin").isEqualTo(403);
        assertThat(lastEvent()).containsEntry("resource", "cluster").containsEntry("accessType", "admin");
        assertThat(status(RequestContext.of("root").collections("sales").handler("custom").type(null))).isEqualTo(200);
        assertThat(status(RequestContext.of("readers-member").handler("custom").type(AuthorizationContext.RequestType.READ))).isEqualTo(403);
    }

    @Test
    void administersTheCollectionsARequestNames()
    {
        assertThat(status(admin("carol", PermissionNameProvider.Name.COLL_EDIT_PERM, "action", "create", "name", "team-a"))).isEqualTo(200);
        assertThat(lastEvent()).containsEntry("resource", "collection team-a").containsEntry("request", "action=CREATE")
                .containsEntry("action", "collection-admin-edit");
        assertThat(status(admin("carol", PermissionNameProvider.Name.COLL_EDIT_PERM, "action", "RELOAD", "collection", "team-a,team-b"))).isEqualTo(200);
        assertThat(status(admin("carol", PermissionNameProvider.Name.COLL_EDIT_PERM, "action", "RELOAD", "collection", "team-a, sales"))).isEqualTo(403);
        assertThat(status(admin("carol", PermissionNameProvider.Name.COLL_READ_PERM, "action", "CLUSTERSTATUS", "collection", "team-a"))).isEqualTo(200);
        assertThat(status(admin("carol", PermissionNameProvider.Name.COLL_READ_PERM, "action", "LIST"))).isEqualTo(403);
        assertThat(status(admin("carol", PermissionNameProvider.Name.COLL_EDIT_PERM, "action", "ADDROLE"))).isEqualTo(403);
        assertThat(status(admin("carol", PermissionNameProvider.Name.COLL_EDIT_PERM).collections("team-c"))).isEqualTo(200);
        assertThat(status(admin("root", PermissionNameProvider.Name.COLL_EDIT_PERM, "action", "DELETE", "name", "sales"))).isEqualTo(200);
    }

    @Test
    void administersTheClusterUnderPolicy()
    {
        assertThat(status(admin("dave", PermissionNameProvider.Name.METRICS_READ_PERM))).isEqualTo(200);
        assertThat(status(admin("dave", PermissionNameProvider.Name.HEALTH_PERM))).isEqualTo(200);
        assertThat(status(admin("dave", PermissionNameProvider.Name.COLL_READ_PERM))).isEqualTo(403);
        Principal reader = new Reader();
        for (PermissionNameProvider.Name read : List.of(PermissionNameProvider.Name.COLL_READ_PERM, PermissionNameProvider.Name.CORE_READ_PERM,
                PermissionNameProvider.Name.ZK_READ_PERM, PermissionNameProvider.Name.METRICS_READ_PERM, PermissionNameProvider.Name.CONFIG_READ_PERM,
                PermissionNameProvider.Name.SCHEMA_READ_PERM)) {
            assertThat(status(admin("x", read).principal(reader))).as(read.name()).isEqualTo(200);
        }
        assertThat(status(RequestContext.of("x").principal(reader).handler("custom").type(AuthorizationContext.RequestType.READ))).isEqualTo(200);
        for (PermissionNameProvider.Name edit : List.of(PermissionNameProvider.Name.CORE_EDIT_PERM, PermissionNameProvider.Name.SECURITY_READ_PERM,
                PermissionNameProvider.Name.SECURITY_EDIT_PERM, PermissionNameProvider.Name.PACKAGE_EDIT_PERM, PermissionNameProvider.Name.CONFIG_EDIT_PERM)) {
            assertThat(status(admin("x", edit).principal(reader))).as(edit.name()).isEqualTo(403);
            assertThat(status(admin("root", edit))).as(edit.name()).isEqualTo(200);
        }
    }

    @Test
    void holdsForTheClientAndTrustsOnlyTheProxiesItIsTold()
    {
        assertThat(status(query("erin", "hr", "/select"))).isEqualTo(200);
        assertThat(status(query("erin", "hr", "/select").from("203.0.113.9", null))).isEqualTo(403);
        assertThat(status(query("erin", "hr", "/select").from("192.0.2.1", "10.1.1.1"))).as("a trusted proxy forwards the client").isEqualTo(200);
        assertThat(lastEvent()).containsEntry("clientIp", "10.1.1.1");
        assertThat(status(query("erin", "hr", "/select").from("203.0.113.9", "10.1.1.1"))).as("an untrusted one does not").isEqualTo(403);
        assertThat(status(query("erin", "hr", "/select").from(null, null))).isEqualTo(403);
    }

    @Test
    void signsInTheAnonymousAndLeavesTheNodesUnchecked()
    {
        assertThat(authorizer.authorize(RequestContext.of(null))).isSameAs(AuthorizationResponse.PROMPT);
        int before = test.events().size();
        assertThat(authorizer.authorize(query(GrantForgeSolrAuthorizer.NODE, "hr", "/select"))).isSameAs(AuthorizationResponse.OK);
        assertThat(test.events()).hasSize(before);
    }

    @Test
    void refusesEverythingWithoutPoliciesAndNothingWhileTheServiceIsNotInUse()
    {
        test.snapshot(null);
        assertThat(status(query("alice", "sales", "/select"))).isEqualTo(403);
        test.snapshot(new SolrSnapshots().disabled().snapshot());
        assertThat(status(query("mallory", "sales", "/select"))).isEqualTo(200);
        assertThat(new GrantForgeSolrAuthorizer().authorize(query("alice", "sales", "/select")).getMessage()).contains("has not started");
    }

    @Test
    void refusesWhenDecidingBreaks()
    {
        Snapshot broken = mock(Snapshot.class);
        when(broken.serviceType()).thenReturn("solr");
        when(broken.serviceEnabled()).thenThrow(new IllegalStateException("corrupt"));
        test.snapshot(broken);
        assertThat(authorizer.authorize(query("alice", "sales", "/select")).getMessage()).isEqualTo("GrantForge cannot decide it");
        doReturn(true).when(broken).serviceEnabled();
        when(broken.decide(any())).thenThrow(new IllegalStateException("corrupt"));
        assertThat(status(query("alice", "sales", "/select"))).isEqualTo(403);
    }

    @Test
    void takesAJsonWebTokensVerifiedRolesAsGroups()
    {
        assertThat(status(admin("someone", PermissionNameProvider.Name.CORE_READ_PERM).principal(new Reader()))).isEqualTo(200);
        assertThat(status(admin("someone", PermissionNameProvider.Name.CORE_READ_PERM).principal(new NoRoles()))).isEqualTo(403);
        assertThat(status(query("alice", "sales", "/select").nullCollection())).isEqualTo(200);
    }

    @Test
    void readsItsVersion() throws IOException
    {
        assertThat(GrantForgeSolrAuthorizer.version()).isNotBlank();
        assertThat(GrantForgeSolrAuthorizer.version(new ByteArrayInputStream("grantforge.version= 1.2 \n".getBytes(StandardCharsets.UTF_8))))
                .isEqualTo("1.2");
        assertThatThrownBy(() -> GrantForgeSolrAuthorizer.version(null)).isInstanceOf(IOException.class);
        assertThatThrownBy(() -> GrantForgeSolrAuthorizer.version(new ByteArrayInputStream("other=1".getBytes(StandardCharsets.UTF_8))))
                .isInstanceOf(IOException.class);
        assertThat(authorizer).hasToString("GrantForge");
        authorizer.close();
    }

    @Test
    void startsOneAgentAndReplacesItWhenTheSettingsChange() throws IOException
    {
        Map<String, Object> config = new HashMap<>();
        config.put("class", GrantForgeSolrAuthorizer.class.getName());
        GrantForgeSolrAuthorizer missing = new GrantForgeSolrAuthorizer();
        assertThatIllegalArgumentException().isThrownBy(() -> missing.init(config)).withMessageContaining("grantforge.");
        config.putAll(TestRuntime.settings(temporary));
        config.put("grantforge.server.url", "http://127.0.0.1:1/");
        config.put("grantforge.connect.timeout.ms", 100);
        config.put("grantforge.trusted.proxies", List.of("192.0.2.0/24", "198.51.100.7"));
        config.put("grantforge.ignored", null);
        GrantForgeSolrAuthorizer first = new GrantForgeSolrAuthorizer();
        first.init(config);
        GrantForgeSolrAuthorizer second = new GrantForgeSolrAuthorizer();
        second.init(config);
        assertThat(first.authorize(query(GrantForgeSolrAuthorizer.NODE, "sales", "/select")).statusCode).isEqualTo(200);
        assertThat(second.authorize(query("alice", "sales", "/select")).statusCode).as("no policies yet").isEqualTo(403);
        config.put("grantforge.trusted.proxies", "192.0.2.0/24");
        GrantForgeSolrAuthorizer third = new GrantForgeSolrAuthorizer();
        third.init(config);
        assertThat(third.authorize(query("alice", "sales", "/select")).statusCode).isEqualTo(403);
        config.put("grantforge.token.file", temporary.resolve("missing").toString());
        assertThatIllegalArgumentException().isThrownBy(() -> new GrantForgeSolrAuthorizer().init(config)).withMessageContaining("cannot start");
    }

    /** A JSON web token's user whose verified roles include readers. */
    private static final class Reader
            implements Principal, VerifiedUserRoles
    {
        @Override
        public String getName()
        {
            return "readers-member";
        }

        @Override
        public Set<String> getVerifiedRoles()
        {
            return Set.of("readers");
        }
    }

    /** A JSON web token's user without verified roles. */
    private static final class NoRoles
            implements Principal, VerifiedUserRoles
    {
        @Override
        public String getName()
        {
            return "nobody";
        }

        @Override
        @SuppressWarnings("NullAway")
        public @Nullable Set<String> getVerifiedRoles()
        {
            return null;
        }
    }
}

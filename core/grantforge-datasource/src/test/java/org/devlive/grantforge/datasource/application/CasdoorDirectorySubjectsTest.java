// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.datasource.application;

import com.sun.net.httpserver.HttpServer;
import org.devlive.grantforge.common.error.GrantForgeException;
import org.devlive.grantforge.datasource.provider.DirectoryProvider.Employee;
import org.devlive.grantforge.identity.application.IdentitySourceSettings;
import org.devlive.grantforge.identity.application.OidcSettings;
import org.devlive.grantforge.identity.domain.IdentitySource;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CasdoorDirectorySubjectsTest
{
    @Test
    void resolvesOnlyAnEnabledAccountWithBothDirectoryIdentifiers() throws Exception
    {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/get-users", exchange -> {
            byte[] body = """
                    {"status":"ok","data":[
                      {"owner":"test-org","id":"employee-1","name":"union-1"},
                      {"owner":"test-org","id":"some-open-id","name":"existing-user","dingtalk":"oauth-id",
                       "properties":{"oauth_DingTalk_id":"oauth-id","oauth_DingTalk_unionId":"union-2"}},
                      {"owner":"test-org","id":"first-duplicate","name":"existing-a","dingtalk":"oauth-a",
                       "properties":{"oauth_DingTalk_id":"oauth-a","oauth_DingTalk_unionId":"union-4"}},
                      {"owner":"test-org","id":"second-duplicate","name":"existing-b","dingtalk":"oauth-b",
                       "properties":{"oauth_DingTalk_id":"oauth-b","oauth_DingTalk_unionId":"union-4"}},
                      {"owner":"test-org","id":"unlinked","name":"existing-c",
                       "properties":{"oauth_DingTalk_id":"oauth-c","oauth_DingTalk_unionId":"union-5"}},
                      {"owner":"test-org","id":"employee-3","name":"union-3","isForbidden":true}
                    ]}
                    """.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (var output = exchange.getResponseBody()) {
                output.write(body);
            }
        });
        server.start();
        try {
            IdentitySource source = mock(IdentitySource.class);
            when(source.isEnabled()).thenReturn(true);
            IdentitySourceSettings settings = mock(IdentitySourceSettings.class);
            when(settings.secret(source)).thenReturn("fixture-password");
            when(settings.oidc(source)).thenReturn(new OidcSettings("http://127.0.0.1:" + server.getAddress().getPort(),
                    "fixture-client", "", "", "", ""));
            var resolver = new CasdoorDirectorySubjects(settings);
            List<Employee> employees = List.of(employee("employee-1", "union-1"), employee("employee-2", "union-2"),
                    employee("employee-3", "union-3"), employee("employee-4", "union-4"), employee("employee-5", "union-5"));
            assertThat(resolver.resolve(source, Map.of("casdoorOrganization", "test-org"), employees)).isEqualTo(Map.of("employee-1", "employee-1"));
            assertThat(resolver.resolve(source, Map.of("casdoorOrganization", "test-org",
                    CasdoorDirectorySubjects.TRUST_DINGTALK_UNION_ID, "true"), employees))
                    .isEqualTo(Map.of("employee-1", "employee-1", "employee-2", "some-open-id"));
            assertThatThrownBy(() -> resolver.resolve(source, Map.of("casdoorOrganization", "other-org"), employees)).isInstanceOf(GrantForgeException.class);
        }
        finally {
            server.stop(0);
        }
    }

    private static Employee employee(String id, String unionId)
    {
        return new Employee(id, null, null, List.of("1"), unionId, null);
    }
}

// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server;

import jakarta.servlet.http.Cookie;
import org.devlive.grantforge.datasource.domain.DataSource;
import org.devlive.grantforge.datasource.domain.DataSourceRepository;
import org.devlive.grantforge.datasource.domain.DirectoryAccount;
import org.devlive.grantforge.datasource.domain.DirectoryAccountRepository;
import org.devlive.grantforge.identity.domain.Tenant;
import org.devlive.grantforge.identity.domain.TenantRepository;
import org.devlive.grantforge.identity.domain.UserAccount;
import org.devlive.grantforge.identity.domain.UserAccountRepository;
import org.devlive.grantforge.persistence.tenant.TenantContext;
import org.devlive.grantforge.server.security.SecurityConfiguration;
import org.devlive.grantforge.testsupport.TestDatabase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;

import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Starts the whole server on a real database selected with {@code -Dgrantforge.it.database}: every module's
 * changelog applies and validates, and first-run setup plus a full session lifecycle work, including the
 * session attributes Spring Session stores as binary data and the session list.
 */
@SpringBootTest(properties = "grantforge.setup.token=" + ServerDatabaseIT.TOKEN)
@AutoConfigureMockMvc
class ServerDatabaseIT
{
    static final String TOKEN = "server-database-it-0123456789";
    private static final TestDatabase DATABASE = TestDatabase.fromSystemProperty();
    private static final String PASSWORD = "a long enough password";

    @Autowired
    private MockMvc mvc;
    @Autowired
    private TenantRepository tenants;
    @Autowired
    private UserAccountRepository users;
    @Autowired
    private DataSourceRepository sources;
    @Autowired
    private DirectoryAccountRepository bindings;
    @Autowired
    private PlatformTransactionManager transactions;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry)
    {
        registry.add("spring.datasource.url", DATABASE::url);
        registry.add("spring.datasource.username", DATABASE::username);
        registry.add("spring.datasource.password", DATABASE::password);
    }

    @AfterAll
    static void stopDatabase()
    {
        DATABASE.close();
    }

    @Test
    void setupSignInAndSignOut() throws Exception
    {
        mvc.perform(post("/api/v1/setup").with(csrf()).contentType(MediaType.APPLICATION_JSON).content("""
                        {"token": "%s", "tenantName": "权限管理-🔐", "username": "admin", "password": "%s"}
                        """.formatted(TOKEN, PASSWORD)))
                .andExpect(status().isOk());

        Cookie session = requireNonNull(mvc.perform(post("/api/v1/auth/login").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\": \"ADMIN\", \"password\": \"%s\"}".formatted(PASSWORD)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getCookie(SecurityConfiguration.SESSION_COOKIE));

        mvc.perform(get("/api/v1/me").cookie(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tenantName").value("权限管理-🔐"));
        mvc.perform(get("/api/v1/tenants").param("q", "权限").cookie(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].platform").value(true))
                .andExpect(jsonPath("$.items[0].accounts").value(1));
        mvc.perform(get("/api/v1/sessions").cookie(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].current").value(true));
        mvc.perform(post("/api/v1/auth/logout").with(csrf()).cookie(session)).andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/me").cookie(session)).andExpect(status().isUnauthorized());
        pendingDirectoryBindings();
    }

    private void pendingDirectoryBindings()
    {
        long tenant = tenants.save(Tenant.create("directory-test", "Directory test")).requireId();
        TenantContext.runInTenant(tenant, () -> {
            TransactionTemplate transaction = new TransactionTemplate(transactions);
            long source = requireNonNull(transaction.execute(status -> {
                DataSource config = DataSource.create("directory", "DINGTALK");
                config.configure("Directory", 1, true, true, "{}", null, null);
                long id = sources.save(config).requireId();
                long first = users.save(UserAccount.create("first", "unused", Instant.now())).requireId();
                long second = users.save(UserAccount.create("second", "unused", Instant.now())).requireId();
                bindings.save(DirectoryAccount.of(id, first));
                bindings.save(DirectoryAccount.of(id, second));
                bindings.flush();
                return id;
            }));
            assertThat(bindings.findBySourceId(source)).hasSize(2);
            transaction.executeWithoutResult(status -> {
                var first = bindings.findBySourceId(source).get(0);
                first.bind("employee-1");
                bindings.saveAndFlush(first);
            });
            assertThatThrownBy(() -> transaction.executeWithoutResult(status -> {
                var second = bindings.findBySourceId(source).stream().filter(link -> link.getEmployeeId() == null).findFirst().orElseThrow();
                second.bind("employee-1");
                bindings.saveAndFlush(second);
            })).isInstanceOf(DataIntegrityViolationException.class);
            transaction.executeWithoutResult(status -> sources.deleteById(source));
            assertThat(bindings.findBySourceId(source)).isEmpty();
        });
    }
}

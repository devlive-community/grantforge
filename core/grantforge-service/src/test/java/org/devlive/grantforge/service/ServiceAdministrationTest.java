// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.service;

import org.devlive.grantforge.audit.application.AuditLog;
import org.devlive.grantforge.audit.domain.AuditEventRepository;
import org.devlive.grantforge.common.error.CommonErrorCode;
import org.devlive.grantforge.common.error.ErrorCode;
import org.devlive.grantforge.common.error.FieldIssue;
import org.devlive.grantforge.common.error.GrantForgeException;
import org.devlive.grantforge.identity.domain.PlatformSettingRepository;
import org.devlive.grantforge.identity.domain.Tenant;
import org.devlive.grantforge.identity.domain.TenantRepository;
import org.devlive.grantforge.persistence.tenant.TenantContext;
import org.devlive.grantforge.plugin.api.ConnectionResult;
import org.devlive.grantforge.plugin.host.PluginRegistry;
import org.devlive.grantforge.plugin.host.domain.PluginStateRepository;
import org.devlive.grantforge.service.domain.ManagedService;
import org.devlive.grantforge.service.domain.ManagedServiceRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@Import({ServiceAdministration.class, SecretBox.class, AuditLog.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ServiceAdministrationTest
{
    private static final Map<String, String> VALID = Map.of("url", "demo://cluster", "mode", "safe", "password", "s3cret");

    @Autowired
    private ServiceAdministration administration;

    @Autowired
    private ManagedServiceRepository services;

    @Autowired
    private TenantRepository tenants;

    @Autowired
    private PluginRegistry plugins;

    @Autowired
    private PluginStateRepository pluginStates;

    @Autowired
    private PlatformSettingRepository settings;

    @Autowired
    private AuditEventRepository events;

    private long acme;
    private long globex;

    @BeforeEach
    void createTenants()
    {
        plugins.scan();
        acme = tenants.save(Tenant.create("acme", "Acme")).requireId();
        globex = tenants.save(Tenant.create("globex", "Globex")).requireId();
    }

    @AfterEach
    void deleteRows()
    {
        TenantContext.callAsSystem(() -> {
            services.deleteAllInBatch();
            return null;
        });
        events.deleteAllInBatch();
        pluginStates.deleteAll();
        settings.deleteAll();
        tenants.deleteAllInBatch();
        plugins.scan();
    }

    private <T> T inAcme(Supplier<T> action)
    {
        return TenantContext.callInTenant(acme, action::get);
    }

    private static ServiceCommand command(String name, Map<String, String> values)
    {
        return new ServiceCommand(name, "  " + name.toUpperCase(Locale.ROOT) + " ", null, true, values);
    }

    private static Map<String, String> with(Map<String, String> base, String key, String value)
    {
        Map<String, String> changed = new HashMap<>(base);
        changed.put(key, value);
        return changed;
    }

    private static void assertRefused(Runnable action, ErrorCode code, String... fields)
    {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(GrantForgeException.class, error -> {
            assertThat(error.getErrorCode()).isEqualTo(code);
            assertThat(error.getFieldIssues()).extracting(FieldIssue::field).containsExactlyInAnyOrder(fields);
        });
    }

    @Test
    void createsServicesWithSealedSecretsThatAreNeverShown()
    {
        ServiceView created = inAcme(() -> administration.create(7, "demo", command("hive-prod", VALID)));

        assertThat(created).extracting(ServiceView::name, ServiceView::label, ServiceView::serviceType, ServiceView::serviceTypeLabel,
                ServiceView::enabled).containsExactly("hive-prod", "HIVE-PROD", "demo", "Demo", true);
        assertThat(created.available()).isTrue();
        // Only values that were set are stored; the default timeout is not, and the secret is never shown.
        assertThat(created.values()).containsOnly(Map.entry("url", "demo://cluster"), Map.entry("mode", "safe"));
        assertThat(created.secretsSet()).containsExactly("password");
        ManagedService stored = inAcme(() -> services.findById(created.id()).orElseThrow());
        assertThat(stored.getSecrets()).doesNotContain("s3cret").contains("v1:");
        assertThat(inAcme(() -> administration.list())).extracting(ServiceView::name).containsExactly("hive-prod");
        assertThat(inAcme(() -> administration.find(created.id())).name()).isEqualTo("hive-prod");
        // Another tenant sees none of it.
        assertThat(TenantContext.callInTenant(globex, () -> administration.list())).isEmpty();
        assertThat(administration.serviceTypes()).extracting(type -> type.name()).containsExactly("demo");
    }

    @Test
    void refusesConfigurationsTheTypeOrThePluginRejects()
    {
        assertRefused(() -> inAcme(() -> administration.create(7, "demo", command("x1", Map.of("timeout", "soon", "ssl", "maybe",
                "mode", "slow", "colour", "red")))), ServiceErrorCode.CONFIG_INVALID, "url", "password", "timeout", "ssl", "mode", "colour");
        assertRefused(() -> inAcme(() -> administration.create(7, "demo", command("x1", with(VALID, "url", "http://x")))),
                ServiceErrorCode.CONFIG_INVALID, "url");
        // The plugin's own rule only runs once the fields themselves are fine.
        assertRefused(() -> inAcme(() -> administration.create(7, "demo", command("x1", with(VALID, "ssl", "true")))),
                ServiceErrorCode.CONFIG_INVALID, "ssl");
        assertRefused(() -> inAcme(() -> administration.create(7, "demo", new ServiceCommand("Bad Name", " ", "x".repeat(600), true,
                VALID))), CommonErrorCode.BAD_REQUEST, "name", "label", "description");
        assertRefused(() -> inAcme(() -> administration.create(7, "nope", command("x1", VALID))), ServiceErrorCode.TYPE_UNAVAILABLE);
        inAcme(() -> administration.create(7, "demo", command("x1", VALID)));
        assertRefused(() -> inAcme(() -> administration.create(7, "demo", command("x1", VALID))), ServiceErrorCode.NAME_TAKEN);
        assertThat(inAcme(() -> services.count())).isEqualTo(1);
    }

    @Test
    void updatesKeepStoredSecretsUnlessNewOnesAreGiven()
    {
        long id = inAcme(() -> administration.create(7, "demo", command("hive", VALID))).id();
        Map<String, String> withoutPassword = with(VALID, "password", "");

        ServiceView renamed = inAcme(() -> administration.update(7, id, new ServiceCommand("hive-new", "Hive", "the main one", false,
                withoutPassword)));
        assertThat(renamed).extracting(ServiceView::name, ServiceView::description, ServiceView::enabled)
                .containsExactly("hive-new", "the main one", false);
        assertThat(renamed.secretsSet()).containsExactly("password");
        assertThat(inAcme(() -> administration.test("demo", id, "hive", withoutPassword))).isEqualTo(ConnectionResult.succeeded());

        inAcme(() -> administration.update(7, id, command("hive-new", with(VALID, "password", "other"))));
        assertThat(inAcme(() -> administration.test("demo", id, "hive", withoutPassword)).message()).isEqualTo("wrong password");
        long other = inAcme(() -> administration.create(7, "demo", command("other", VALID))).id();
        assertRefused(() -> inAcme(() -> administration.update(7, other, command("hive-new", VALID))), ServiceErrorCode.NAME_TAKEN);
        assertRefused(() -> inAcme(() -> administration.update(7, 42, command("x1", VALID))), CommonErrorCode.NOT_FOUND);
    }

    @Test
    void testsConnectionsAndReportsPluginFailuresAsFailedConnections()
    {
        assertThat(inAcme(() -> administration.test("demo", null, "new", VALID))).isEqualTo(ConnectionResult.succeeded());
        assertThat(inAcme(() -> administration.test("demo", null, "new", with(VALID, "url", "demo://down"))).message())
                .isEqualTo("cannot reach demo://down");
        assertThat(inAcme(() -> administration.test("demo", null, "new", with(VALID, "url", "demo://slow"))).message())
                .contains("did not answer within 1s");
        assertRefused(() -> inAcme(() -> administration.test("demo", null, "new", Map.of())), ServiceErrorCode.CONFIG_INVALID, "url",
                "password");
        assertRefused(() -> inAcme(() -> administration.test("demo", 42L, "new", VALID)), CommonErrorCode.NOT_FOUND);
    }

    @Test
    void looksUpResourcesThroughThePlugin()
    {
        long id = inAcme(() -> administration.create(7, "demo", command("hive", VALID))).id();
        long broken = inAcme(() -> administration.create(7, "demo", command("broken", with(VALID, "url", "demo://broken")))).id();

        assertThat(inAcme(() -> administration.lookup(id, "database", "op", Map.of(), 10))).containsExactly("ops", "operations");
        assertThat(inAcme(() -> administration.lookup(id, "database", "", Map.of(), 2))).containsExactly("hr", "ops");
        assertRefused(() -> inAcme(() -> administration.lookup(id, "table", "", Map.of(), 10)), ServiceErrorCode.LOOKUP_UNSUPPORTED);
        assertRefused(() -> inAcme(() -> administration.lookup(id, "column", "", Map.of(), 10)), ServiceErrorCode.LOOKUP_UNSUPPORTED);
        assertRefused(() -> inAcme(() -> administration.lookup(broken, "database", "", Map.of(), 10)), ServiceErrorCode.PLUGIN_FAILED);
    }

    @Test
    void servicesOfASwitchedOffTypeStayButCannotBeUsedAndCanBeDeleted()
    {
        long id = inAcme(() -> administration.create(7, "demo", command("hive", VALID))).id();
        plugins.setEnabled("builtin-demo", false);

        ServiceView unavailable = inAcme(() -> administration.find(id));
        assertThat(unavailable.available()).isFalse();
        assertThat(unavailable.serviceTypeLabel()).isNull();
        assertRefused(() -> inAcme(() -> administration.lookup(id, "database", "", Map.of(), 10)), ServiceErrorCode.TYPE_UNAVAILABLE);

        inAcme(() -> {
            administration.delete(7, id);
            return null;
        });
        assertThat(inAcme(() -> administration.list())).isEmpty();
        assertRefused(() -> inAcme(() -> {
            administration.delete(7, id);
            return null;
        }), CommonErrorCode.NOT_FOUND);
        assertThat(events.findAll()).extracting(event -> event.getAction().name())
                .containsExactlyInAnyOrder("SERVICE_CREATED", "SERVICE_DELETED");
        assertThat(List.of(acme, globex)).doesNotContainNull();
    }
}

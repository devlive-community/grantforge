// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.service.agent;

import org.devlive.grantforge.common.error.GrantForgeException;
import org.devlive.grantforge.identity.domain.PlatformSettingRepository;
import org.devlive.grantforge.identity.domain.Tenant;
import org.devlive.grantforge.identity.domain.TenantRepository;
import org.devlive.grantforge.persistence.tenant.TenantContext;
import org.devlive.grantforge.plugin.api.model.MatcherType;
import org.devlive.grantforge.plugin.api.model.PolicyType;
import org.devlive.grantforge.plugin.host.PluginRegistry;
import org.devlive.grantforge.plugin.host.domain.PluginStateRepository;
import org.devlive.grantforge.identity.application.SecretBox;
import org.devlive.grantforge.service.ServiceErrorCode;
import org.devlive.grantforge.service.domain.ManagedService;
import org.devlive.grantforge.service.domain.ManagedServiceRepository;
import org.devlive.grantforge.service.domain.PolicyPriority;
import org.devlive.grantforge.service.domain.ServicePolicy;
import org.devlive.grantforge.service.domain.ServicePolicyRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest(properties = "grantforge.security.encryption-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=")
@Import({PolicySnapshots.class, SnapshotSigner.class, SecretBox.class, FakeSnapshotSubjects.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class PolicySnapshotsTest
{
    private static final String ACCESS = """
            {"resources": {"database": {"values": ["sales"], "excludes": false, "recursive": false}},
             "allow": [{"users": ["dave"], "groups": ["public", "ops"], "roles": ["analyst"], "accessTypes": ["select"]}],
             "deny": [{"groups": ["spies"], "roles": ["boss"], "accessTypes": ["update"]}],
             "validity": [{"from": "2026-01-01T00:00:00Z"}]}
            """;

    @Autowired
    private PolicySnapshots snapshots;

    @Autowired
    private SnapshotSigner signer;

    @Autowired
    private ServicePolicyRepository policies;

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

    private long acme;
    private long warehouse;

    @BeforeEach
    void createService()
    {
        plugins.scan();
        acme = tenants.save(Tenant.create("acme", "Acme")).requireId();
        warehouse = TenantContext.callInTenant(acme, () -> {
            ManagedService service = ManagedService.create("warehouse", "dw", "DW", null);
            service.policiesChanged();
            return services.save(service).requireId();
        });
    }

    @AfterEach
    void deleteRows()
    {
        TenantContext.callAsSystem(() -> {
            policies.deleteAllInBatch();
            services.deleteAllInBatch();
            return null;
        });
        pluginStates.deleteAll();
        settings.deleteAll();
        tenants.deleteAllInBatch();
        plugins.scan();
    }

    private void policy(PolicyType type, String name, boolean enabled, String body)
    {
        TenantContext.runInTenant(acme, () -> {
            ServicePolicy policy = ServicePolicy.create(warehouse, type);
            policy.describe(name, null, PolicyPriority.NORMAL, enabled, "[]", body);
            policies.save(policy);
        });
    }

    @Test
    void carriesTheEnabledPoliciesTheTypeAndTheMembersOfNamedRolesAndGroups()
    {
        policy(PolicyType.ACCESS, "sales", true, ACCESS);
        policy(PolicyType.ACCESS, "off", false, ACCESS.replace("analyst", "auditor"));
        policy(PolicyType.DATA_MASK, "mask", true, """
                {"resources": {"database": {"values": ["hr"], "excludes": false, "recursive": false},
                               "table": {"values": ["people"], "excludes": false, "recursive": false},
                               "column": {"values": ["ssn"], "excludes": false, "recursive": false}},
                 "allow": [{"groups": ["public"], "accessTypes": ["select"], "maskType": "redact"}]}
                """);

        PolicySnapshot snapshot = TenantContext.callInTenant(acme, () -> snapshots.build(warehouse));
        assertThat(snapshot).extracting(PolicySnapshot::format, PolicySnapshot::service, PolicySnapshot::serviceType,
                PolicySnapshot::serviceEnabled, PolicySnapshot::policyVersion).containsExactly(1, "dw", "warehouse", true, 1L);
        assertThat(snapshot.policies()).extracting(PolicySnapshot.SnapshotPolicy::name).containsExactly("sales", "mask");
        assertThat(snapshot.policies().get(1).type()).isEqualTo(PolicyType.DATA_MASK);
        assertThat(snapshot.roles()).isEqualTo(Map.of("analyst", List.of("alice", "bob"), "boss", List.of()));
        assertThat(snapshot.groups()).isEqualTo(Map.of("ops", List.of("carol"), "spies", List.of()));
        assertThat(snapshot.definition().resources()).contains(new PolicySnapshot.Level("path", null, MatcherType.PATH, true));
        assertThat(snapshot.definition().accessTypes()).last().isEqualTo(new PolicySnapshot.Access("all", List.of("select", "update")));
        assertThat(snapshot.definition().conditions()).extracting(PolicySnapshot.Condition::evaluator).containsExactly("ip-range");
        assertThat(snapshot.definition().maskTypes()).extracting(PolicySnapshot.Mask::name).containsExactly("redact", "custom");

        SignedSnapshot signed = TenantContext.callInTenant(acme, () -> snapshots.signed(warehouse));
        assertThat(PolicySnapshots.decode(signed.body())).isEqualTo(snapshot);
        assertThat(new String(signed.body(), StandardCharsets.UTF_8)).startsWith("{\"format\":1,\"service\":\"dw\"");
        assertThat(signed.keyId()).isEqualTo(signer.publicKey().keyId());
        assertThat(signed.policyVersion()).isEqualTo(1);
        // The same policies give the same bytes, so agents can skip downloads they already have.
        SignedSnapshot again = TenantContext.callInTenant(acme, () -> snapshots.signed(warehouse));
        assertThat(again.etag()).isEqualTo(signed.etag()).matches("\"[0-9a-f]{32}\"");
        assertThat(again.body()).isEqualTo(signed.body());
        policy(PolicyType.ROW_FILTER, "rows", true, """
                {"resources": {"database": {"values": ["hr"], "excludes": false, "recursive": false},
                               "table": {"values": ["people"], "excludes": false, "recursive": false}},
                 "allow": [{"users": ["dave"], "accessTypes": ["select"], "rowFilter": "region = 'eu'"}]}
                """);
        assertThat(TenantContext.callInTenant(acme, () -> snapshots.signed(warehouse)).etag()).isNotEqualTo(signed.etag());
    }

    @Test
    void anEmptyServiceAsksForNoMembersAndAnUnavailableTypeGivesNoSnapshot()
    {
        PolicySnapshot empty = TenantContext.callInTenant(acme, () -> snapshots.build(warehouse));
        assertThat(empty.policies()).isEmpty();
        assertThat(empty.roles()).isEmpty();
        assertThat(empty.groups()).isEmpty();
        assertThatThrownBy(() -> TenantContext.callInTenant(acme, () -> snapshots.build(424242))).isInstanceOf(GrantForgeException.class);

        plugins.setEnabled("builtin-warehouse", false);
        assertThatThrownBy(() -> TenantContext.callInTenant(acme, () -> snapshots.build(warehouse)))
                .isInstanceOfSatisfying(GrantForgeException.class, error -> assertThat(error.getErrorCode())
                        .isEqualTo(ServiceErrorCode.TYPE_UNAVAILABLE));
    }

    @Test
    void storedPoliciesMustBeReadable()
    {
        policy(PolicyType.ACCESS, "broken", true, "{not json");
        assertThatThrownBy(() -> TenantContext.callInTenant(acme, () -> snapshots.build(warehouse))).isInstanceOf(IllegalStateException.class);
    }
}

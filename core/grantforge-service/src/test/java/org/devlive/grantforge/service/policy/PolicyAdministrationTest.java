// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.service.policy;

import org.devlive.grantforge.audit.application.AuditLog;
import org.devlive.grantforge.audit.domain.AuditEventRepository;
import org.devlive.grantforge.common.error.CommonErrorCode;
import org.devlive.grantforge.common.error.ErrorCode;
import org.devlive.grantforge.common.error.FieldIssue;
import org.devlive.grantforge.common.error.GrantForgeException;
import org.devlive.grantforge.identity.domain.Tenant;
import org.devlive.grantforge.identity.domain.TenantRepository;
import org.devlive.grantforge.persistence.tenant.TenantContext;
import org.devlive.grantforge.plugin.api.model.PolicyType;
import org.devlive.grantforge.plugin.host.PluginRegistry;
import org.devlive.grantforge.plugin.host.domain.PluginStateRepository;
import org.devlive.grantforge.service.ServiceErrorCode;
import org.devlive.grantforge.service.domain.ManagedService;
import org.devlive.grantforge.service.domain.ManagedServiceRepository;
import org.devlive.grantforge.service.domain.PolicyPriority;
import org.devlive.grantforge.service.domain.ServicePolicyRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@Import({PolicyAdministration.class, FakePolicySubjects.class, AuditLog.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class PolicyAdministrationTest
{
    private static final Map<String, ResourceValues> SALES = Map.of("database", ResourceValues.of("sales"));
    private static final PolicyItemSpec ALICE_SELECTS = PolicyItemSpec.access(List.of("alice"), List.of(), List.of("select"));

    @Autowired
    private PolicyAdministration administration;

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
    private AuditEventRepository events;

    private long acme;
    private long globex;
    private long warehouse;

    @BeforeEach
    void createService()
    {
        plugins.scan();
        acme = tenants.save(Tenant.create("acme", "Acme")).requireId();
        globex = tenants.save(Tenant.create("globex", "Globex")).requireId();
        warehouse = inAcme(() -> services.save(ManagedService.create("warehouse", "dw", "DW", null)).requireId());
    }

    @AfterEach
    void deleteRows()
    {
        TenantContext.callAsSystem(() -> {
            policies.deleteAllInBatch();
            services.deleteAllInBatch();
            return null;
        });
        events.deleteAllInBatch();
        pluginStates.deleteAll();
        tenants.deleteAllInBatch();
        plugins.scan();
    }

    private <T> T inAcme(Supplier<T> action)
    {
        return TenantContext.callInTenant(acme, action::get);
    }

    private static PolicyCommand command(String name, PolicyDocument document)
    {
        return new PolicyCommand(name, " sales team ", PolicyPriority.NORMAL, true, List.of(" pii ", "pii", "eu"), document);
    }

    private static void assertRefused(Runnable action, ErrorCode code, String... fields)
    {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(GrantForgeException.class, error -> {
            assertThat(error.getErrorCode()).isEqualTo(code);
            assertThat(error.getFieldIssues()).extracting(FieldIssue::field).containsExactlyInAnyOrder(fields);
        });
    }

    private long policyVersion()
    {
        return inAcme(() -> services.findById(warehouse).orElseThrow().getPolicyVersion());
    }

    @Test
    void addsChangesAndRemovesPoliciesCountingEveryChange()
    {
        Instant start = Instant.parse("2026-01-01T00:00:00Z");
        PolicyDocument document = new PolicyDocument(SALES, List.of(ALICE_SELECTS), List.of(), List.of(), List.of(),
                List.of(new ValidityPeriod(start, null)));
        PolicyView created = inAcme(() -> administration.create(7, warehouse, PolicyType.ACCESS, command(" sales ", document)));

        assertThat(created).extracting(PolicyView::serviceId, PolicyView::name, PolicyView::description, PolicyView::type,
                PolicyView::priority, PolicyView::enabled, PolicyView::labels)
                .containsExactly(warehouse, "sales", "sales team", PolicyType.ACCESS, PolicyPriority.NORMAL, true, List.of("pii", "eu"));
        assertThat(created.document()).isEqualTo(document);
        assertThat(created.document().validity().get(0).from()).isEqualTo(start);
        assertThat(policyVersion()).isEqualTo(1);
        assertThat(inAcme(() -> administration.find(created.id()))).isEqualTo(created);
        assertThat(inAcme(() -> administration.list(warehouse, PolicyType.ACCESS))).containsExactly(created);
        assertThat(inAcme(() -> administration.list(warehouse, PolicyType.DATA_MASK))).isEmpty();

        PolicyCommand change = new PolicyCommand("sales readers", null, PolicyPriority.OVERRIDE, false, List.of(),
                PolicyDocument.allowing(SALES, PolicyItemSpec.access(List.of(), List.of("ops", PolicyItemSpec.PUBLIC), List.of("all"))));
        PolicyView updated = inAcme(() -> administration.update(7, created.id(), created.version(), change));
        assertThat(updated).extracting(PolicyView::name, PolicyView::priority, PolicyView::enabled, PolicyView::labels)
                .containsExactly("sales readers", PolicyPriority.OVERRIDE, false, List.of());
        assertThat(updated.version()).isGreaterThan(created.version());
        assertThat(policyVersion()).isEqualTo(2);
        // A change made on an older version would overwrite this one.
        assertRefused(() -> inAcme(() -> administration.update(7, created.id(), created.version(), change)), CommonErrorCode.CONFLICT);
        assertThat(inAcme(() -> administration.update(7, created.id(), null, change)).name()).isEqualTo("sales readers");

        inAcme(() -> {
            administration.delete(7, created.id());
            return null;
        });
        assertThat(inAcme(() -> administration.list(warehouse, PolicyType.ACCESS))).isEmpty();
        assertThat(policyVersion()).isEqualTo(4);
        assertThat(events.findAll()).extracting(event -> event.getAction().name() + " " + event.getReason())
                .containsExactlyInAnyOrder("POLICY_CREATED dw: sales", "POLICY_UPDATED dw: sales readers",
                        "POLICY_UPDATED dw: sales readers", "POLICY_DELETED dw: sales readers");
    }

    @Test
    void refusesUnsoundPoliciesUnknownSubjectsAndTakenNames()
    {
        PolicyItemSpec strangers = new PolicyItemSpec(List.of("alice", "mallory"), List.of("public", "spies"), List.of("boss"),
                List.of("select"), List.of(), null, null, null);
        PolicyDocument unsound = new PolicyDocument(Map.of("table", ResourceValues.of("t")), List.of(), List.of(), List.of(strangers),
                List.of(), List.of());
        assertRefused(() -> inAcme(() -> administration.create(7, warehouse, PolicyType.ACCESS, command("x", unsound))),
                ServiceErrorCode.POLICY_INVALID, "resources", "deny[0].users", "deny[0].groups", "deny[0].roles");
        assertThatThrownBy(() -> inAcme(() -> administration.create(7, warehouse, PolicyType.ACCESS, command("x", unsound))))
                .isInstanceOfSatisfying(GrantForgeException.class, error -> assertThat(error.getFieldIssues())
                        .filteredOn(issue -> issue.field().equals("deny[0].users")).extracting(FieldIssue::arguments)
                        .containsExactly(List.of("mallory")));

        PolicyDocument sound = PolicyDocument.allowing(SALES, ALICE_SELECTS);
        long id = inAcme(() -> administration.create(7, warehouse, PolicyType.ACCESS, command("sales", sound))).id();
        long other = inAcme(() -> administration.create(7, warehouse, PolicyType.ACCESS, command("other", sound))).id();
        assertRefused(() -> inAcme(() -> administration.create(7, warehouse, PolicyType.ACCESS, command("sales", sound))),
                ServiceErrorCode.POLICY_NAME_TAKEN);
        assertRefused(() -> inAcme(() -> administration.update(7, other, null, command("sales", sound))), ServiceErrorCode.POLICY_NAME_TAKEN);
        assertThat(inAcme(() -> administration.update(7, id, null, command("sales", sound))).name()).isEqualTo("sales");
        assertRefused(() -> inAcme(() -> administration.update(7, id, null, command("sales", unsound))), ServiceErrorCode.POLICY_INVALID,
                "resources", "deny[0].users", "deny[0].groups", "deny[0].roles");
        // Masking items need a masking method, and only columns can be masked.
        assertRefused(() -> inAcme(() -> administration.create(7, warehouse, PolicyType.DATA_MASK, command("mask", sound))),
                ServiceErrorCode.POLICY_INVALID, "resources", "allow[0].maskType");
    }

    @Test
    void policiesBelongToServicesOfTheTenantWhoseTypeIsAvailable()
    {
        PolicyDocument sound = PolicyDocument.allowing(SALES, ALICE_SELECTS);
        assertRefused(() -> inAcme(() -> administration.list(424242, PolicyType.ACCESS)), CommonErrorCode.NOT_FOUND);
        assertRefused(() -> inAcme(() -> administration.find(424242)), CommonErrorCode.NOT_FOUND);
        assertRefused(() -> inAcme(() -> {
            administration.delete(7, 424242);
            return null;
        }), CommonErrorCode.NOT_FOUND);
        assertRefused(() -> TenantContext.callInTenant(globex, () -> administration.create(7, warehouse, PolicyType.ACCESS,
                command("x", sound))), CommonErrorCode.NOT_FOUND);

        long id = inAcme(() -> administration.create(7, warehouse, PolicyType.ACCESS, command("sales", sound))).id();
        plugins.setEnabled("builtin-warehouse", false);
        assertRefused(() -> inAcme(() -> administration.update(7, id, null, command("sales", sound))), ServiceErrorCode.TYPE_UNAVAILABLE);
        // What exists stays readable and removable.
        assertThat(inAcme(() -> administration.list(warehouse, PolicyType.ACCESS))).hasSize(1);
        inAcme(() -> {
            administration.delete(7, id);
            return null;
        });
        assertRefused(() -> TenantContext.callInTenant(globex, () -> administration.list(warehouse, PolicyType.ACCESS)),
                CommonErrorCode.NOT_FOUND);
    }

    @Test
    void suggestsSubjectsWithinLimits()
    {
        assertThat(inAcme(() -> administration.subjects(SubjectKind.USER, " ", 10))).containsExactly("alice", "bob");
        assertThat(inAcme(() -> administration.subjects(SubjectKind.USER, "o", 0))).containsExactly("bob");
        assertThat(inAcme(() -> administration.subjects(SubjectKind.ROLE, "", 500))).containsExactly("analyst");
    }
}

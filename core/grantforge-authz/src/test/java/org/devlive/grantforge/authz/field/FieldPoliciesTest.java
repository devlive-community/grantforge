// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.authz.field;

import jakarta.persistence.EntityManagerFactory;
import org.devlive.grantforge.audit.application.AuditLog;
import org.devlive.grantforge.audit.domain.AuditEventRepository;
import org.devlive.grantforge.authz.application.AuthorizationEvaluator;
import org.devlive.grantforge.authz.application.AuthorizationVersions;
import org.devlive.grantforge.authz.application.EffectiveRoles;
import org.devlive.grantforge.authz.application.SubjectDirectory;
import org.devlive.grantforge.authz.data.DataScopeFixture;
import org.devlive.grantforge.authz.domain.FieldPolicy;
import org.devlive.grantforge.authz.domain.FieldPolicyRepository;
import org.devlive.grantforge.authz.domain.Role;
import org.devlive.grantforge.authz.domain.RoleAssignment;
import org.devlive.grantforge.authz.domain.RoleAssignmentRepository;
import org.devlive.grantforge.authz.domain.RoleRepository;
import org.devlive.grantforge.authz.domain.SubjectType;
import org.devlive.grantforge.authz.domain.SystemRole;
import org.devlive.grantforge.identity.application.IdentityConfiguration;
import org.devlive.grantforge.identity.domain.OrgMemberRepository;
import org.devlive.grantforge.identity.domain.OrgUnitRepository;
import org.devlive.grantforge.identity.domain.TenantRepository;
import org.devlive.grantforge.identity.domain.UserAccountRepository;
import org.devlive.grantforge.persistence.secured.FieldMode;
import org.devlive.grantforge.persistence.secured.FieldReadMode;
import org.devlive.grantforge.persistence.secured.FieldView;
import org.devlive.grantforge.persistence.secured.FieldWriteMode;
import org.devlive.grantforge.persistence.secured.MaskStrategy;
import org.devlive.grantforge.persistence.tenant.TenantContext;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import({AuditLog.class, IdentityConfiguration.class, SubjectDirectory.class, EffectiveRoles.class, AuthorizationEvaluator.class,
        AuthorizationVersions.class, FieldPolicies.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class FieldPoliciesTest
{
    @Autowired
    private FieldPolicies fields;

    @Autowired
    private RoleRepository roles;

    @Autowired
    private RoleAssignmentRepository assignments;

    @Autowired
    private FieldPolicyRepository policies;

    @Autowired
    private TenantRepository tenants;

    @Autowired
    private UserAccountRepository accounts;

    @Autowired
    private OrgUnitRepository units;

    @Autowired
    private OrgMemberRepository members;

    @Autowired
    private AuditEventRepository events;

    @Autowired
    private EntityManagerFactory factory;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private DataScopeFixture data;

    @BeforeEach
    void createData()
    {
        data = new DataScopeFixture(tenants, accounts, units, members, events, factory, transactionManager);
    }

    @AfterEach
    void deleteData()
    {
        TenantContext.callAsSystem(() -> {
            policies.deleteAllInBatch();
            assignments.deleteAllInBatch();
            roles.deleteAllInBatch();
            return null;
        });
        data.delete();
    }

    private long role(Role role, long holder)
    {
        long id = data.inAcme(() -> roles.save(role).requireId());
        data.inAcme(() -> assignments.save(RoleAssignment.create(id, SubjectType.USER, holder, RoleAssignment.Terms.UNLIMITED)));
        return id;
    }

    private void policy(long roleId, String field, FieldReadMode mode, @Nullable MaskStrategy mask)
    {
        policy(roleId, field, mode, mask, FieldWriteMode.EDITABLE);
    }

    private void policy(long roleId, String field, FieldReadMode mode, @Nullable MaskStrategy mask, FieldWriteMode write)
    {
        data.inAcme(() -> policies.save(FieldPolicy.create(roleId, "user", field, mode, mask, write)));
    }

    private FieldView email(long reader)
    {
        return data.inAcme(() -> fields.read(reader, "user", "email"));
    }

    @Test
    void theMostRevealingPolicyOfTheReadersRolesWins()
    {
        long auditors = role(Role.create("auditors", "Auditors", null), data.alice);
        policy(auditors, "email", FieldReadMode.HIDDEN, null);
        policy(auditors, "lastLoginAt", FieldReadMode.MASKED, MaskStrategy.FULL);
        assertThat(email(data.alice)).isEqualTo(FieldView.HIDDEN);
        assertThat(data.inAcme(() -> fields.read(data.alice, "user", "lastLoginAt"))).isEqualTo(FieldView.masked(MaskStrategy.FULL));
        assertThat(data.inAcme(() -> fields.restricted(data.alice))).containsOnlyKeys("user.email", "user.lastLoginAt")
                .containsEntry("user.email", new FieldMode(FieldView.HIDDEN, FieldWriteMode.EDITABLE));
        assertThat(data.inAcme(() -> fields.restricted(data.bob))).isEmpty();
        assertThat(fields.restricted(data.alice)).isEmpty();
        // A field no role mentions, and a reader without roles, see everything.
        assertThat(data.inAcme(() -> fields.read(data.alice, "user", "phone"))).isEqualTo(FieldView.VISIBLE);
        assertThat(email(data.bob)).isEqualTo(FieldView.VISIBLE);

        // A second role that masks the field reveals more than the first; masks listed first win.
        long readers = role(Role.create("readers", "Readers", null), data.alice);
        policy(readers, "email", FieldReadMode.MASKED, MaskStrategy.PARTIAL);
        assertThat(email(data.alice)).isEqualTo(FieldView.masked(MaskStrategy.PARTIAL));
        long mailers = role(Role.create("mailers", "Mailers", null), data.alice);
        policy(mailers, "email", FieldReadMode.MASKED, MaskStrategy.EMAIL);
        assertThat(email(data.alice)).isEqualTo(FieldView.masked(MaskStrategy.EMAIL));
        long open = role(Role.create("open", "Open", null), data.alice);
        policy(open, "email", FieldReadMode.VISIBLE, null);
        assertThat(email(data.alice)).isEqualTo(FieldView.VISIBLE);

        // Disabled roles say nothing.
        data.inAcme(() -> {
            for (long id : List.of(readers, mailers, open)) {
                Role found = roles.findById(id).orElseThrow();
                found.enable(false);
                roles.save(found);
            }
            return null;
        });
        assertThat(email(data.alice)).isEqualTo(FieldView.HIDDEN);
    }

    @Test
    void administratorsSeeEveryFieldAndReadersOutsideATenantToo()
    {
        long admins = role(Role.system(SystemRole.TENANT_ADMIN), data.carol);
        policy(admins, "email", FieldReadMode.HIDDEN, null);
        assertThat(email(data.carol)).isEqualTo(FieldView.VISIBLE);
        assertThat(fields.read(data.carol, "user", "email")).isEqualTo(FieldView.VISIBLE);
        assertThat(FieldPolicies.merge(List.of())).isEqualTo(FieldMode.OPEN);
        assertThat(data.inAcme(() -> fields.write(data.carol, "user", "email"))).isEqualTo(FieldWriteMode.EDITABLE);
        assertThat(fields.write(data.carol, "user", "email")).isEqualTo(FieldWriteMode.EDITABLE);
    }

    @Test
    void aFieldMayChangeIfAnyRoleThatMentionsItLetsIt()
    {
        long auditors = role(Role.create("auditors", "Auditors", null), data.alice);
        policy(auditors, "email", FieldReadMode.VISIBLE, null, FieldWriteMode.READONLY);
        assertThat(data.inAcme(() -> fields.write(data.alice, "user", "email"))).isEqualTo(FieldWriteMode.READONLY);
        assertThat(data.inAcme(() -> fields.write(data.alice, "user", "phone"))).isEqualTo(FieldWriteMode.EDITABLE);
        assertThat(data.inAcme(() -> fields.write(data.bob, "user", "email"))).isEqualTo(FieldWriteMode.EDITABLE);
        long editors = role(Role.create("editors", "Editors", null), data.alice);
        policy(editors, "email", FieldReadMode.HIDDEN, null, FieldWriteMode.EDITABLE);
        assertThat(data.inAcme(() -> fields.write(data.alice, "user", "email"))).isEqualTo(FieldWriteMode.EDITABLE);
        assertThat(email(data.alice)).isEqualTo(FieldView.VISIBLE);
    }
}

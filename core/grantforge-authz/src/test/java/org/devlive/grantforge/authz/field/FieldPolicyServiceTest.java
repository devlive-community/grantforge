// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.authz.field;

import org.devlive.grantforge.audit.application.AuditLog;
import org.devlive.grantforge.audit.domain.AuditAction;
import org.devlive.grantforge.audit.domain.AuditEvent;
import org.devlive.grantforge.audit.domain.AuditEventRepository;
import org.devlive.grantforge.authz.application.AuthzErrorCode;
import org.devlive.grantforge.authz.domain.Application;
import org.devlive.grantforge.authz.domain.ApplicationRepository;
import org.devlive.grantforge.authz.domain.CatalogTestData;
import org.devlive.grantforge.authz.domain.FieldPolicyRepository;
import org.devlive.grantforge.authz.domain.Resource;
import org.devlive.grantforge.authz.domain.ResourceRepository;
import org.devlive.grantforge.authz.domain.ResourceType;
import org.devlive.grantforge.authz.domain.Role;
import org.devlive.grantforge.authz.domain.RoleRepository;
import org.devlive.grantforge.common.error.CommonErrorCode;
import org.devlive.grantforge.common.error.FieldIssue;
import org.devlive.grantforge.common.error.GrantForgeException;
import org.devlive.grantforge.identity.application.IdentityConfiguration;
import org.devlive.grantforge.identity.domain.Tenant;
import org.devlive.grantforge.identity.domain.TenantRepository;
import org.devlive.grantforge.persistence.secured.FieldReadMode;
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
import org.springframework.data.domain.Sort;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

@DataJpaTest
@Import({AuditLog.class, IdentityConfiguration.class, FieldPolicyService.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class FieldPolicyServiceTest
{
    @Autowired
    private FieldPolicyService service;

    @Autowired
    private FieldPolicyRepository policies;

    @Autowired
    private RoleRepository roles;

    @Autowired
    private ApplicationRepository applications;

    @Autowired
    private ResourceRepository resources;

    @Autowired
    private TenantRepository tenants;

    @Autowired
    private AuditEventRepository events;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private long acme;
    private long auditors;

    @BeforeEach
    void createRoleAndFields()
    {
        long console = applications.save(Application.create("grantforge-console", "Console", null)).requireId();
        Resource user = resources.save(Resource.create(console, null, ResourceType.DATA_ENTITY, "entity:user", CatalogTestData.details("Users"), 0)
                .markBuiltin());
        resources.save(Resource.create(console, user, ResourceType.FIELD, "entity:user.email", CatalogTestData.details("E-mail"), 0).markBuiltin());
        resources.save(Resource.create(console, user, ResourceType.FIELD, "entity:user.phone", CatalogTestData.details("Phone"), 1).markBuiltin());
        // Made by hand, not declared by the code: no field policy can refer to it.
        resources.save(Resource.create(console, user, ResourceType.FIELD, "entity:user.notes", CatalogTestData.details("Notes"), 2));
        acme = tenants.save(Tenant.create("acme", "Acme")).requireId();
        auditors = inAcme(() -> roles.save(Role.create("auditors", "Auditors", null)).requireId());
    }

    @AfterEach
    void deleteRows()
    {
        TenantContext.callAsSystem(() -> {
            policies.deleteAllInBatch();
            roles.deleteAllInBatch();
            return null;
        });
        CatalogTestData.deleteResources(resources, transactionManager);
        applications.deleteAllInBatch();
        events.deleteAllInBatch();
        tenants.deleteAllInBatch();
    }

    private <T> T inAcme(Supplier<T> action)
    {
        return TenantContext.callInTenant(acme, action::get);
    }

    private static FieldPolicyCommand command(String field, FieldReadMode read, @Nullable MaskStrategy mask)
    {
        return new FieldPolicyCommand("user", field, read, mask, FieldWriteMode.EDITABLE);
    }

    @Test
    void replacesTheFieldPoliciesOfARole()
    {
        assertThat(inAcme(() -> service.replace(7, auditors, List.of(command("phone", FieldReadMode.HIDDEN, null),
                new FieldPolicyCommand("user", "email", FieldReadMode.MASKED, MaskStrategy.EMAIL, FieldWriteMode.READONLY)))))
                .extracting(FieldPolicyView::fieldCode, FieldPolicyView::readMode, FieldPolicyView::maskStrategy, FieldPolicyView::writeMode)
                .containsExactly(tuple("email", FieldReadMode.MASKED, MaskStrategy.EMAIL, FieldWriteMode.READONLY),
                        tuple("phone", FieldReadMode.HIDDEN, null, FieldWriteMode.EDITABLE));
        assertThat(inAcme(() -> service.replace(7, auditors, List.of(command("email", FieldReadMode.VISIBLE, null)))))
                .extracting(FieldPolicyView::fieldCode).containsExactly("email");
        assertThat(inAcme(() -> service.list(auditors))).extracting(FieldPolicyView::readMode).containsExactly(FieldReadMode.VISIBLE);
        assertThat(inAcme(() -> service.replace(7, auditors, List.of()))).isEmpty();
        assertThat(events.findAll(Sort.by("occurredAt", "id"))).extracting(AuditEvent::getAction, AuditEvent::getTargetId, AuditEvent::getReason).containsExactly(
                tuple(AuditAction.FIELD_POLICIES_CHANGED, Long.toString(auditors), "2 fields"),
                tuple(AuditAction.FIELD_POLICIES_CHANGED, Long.toString(auditors), "1 fields"),
                tuple(AuditAction.FIELD_POLICIES_CHANGED, Long.toString(auditors), "0 fields"));
    }

    @Test
    void refusesWhatDoesNotFitAndUnknownRoles()
    {
        assertThatThrownBy(() -> inAcme(() -> service.replace(7, auditors, List.of(command("email", FieldReadMode.MASKED, null),
                command("email", FieldReadMode.VISIBLE, null), command("notes", FieldReadMode.HIDDEN, null),
                command("phone", FieldReadMode.HIDDEN, MaskStrategy.FULL)))))
                .isInstanceOfSatisfying(GrantForgeException.class, error -> {
                    assertThat(error.getErrorCode()).isEqualTo(AuthzErrorCode.FIELD_POLICY_INVALID);
                    assertThat(error.getFieldIssues()).extracting(FieldIssue::field, FieldIssue::messageKey).containsExactly(
                            tuple("policies[0].maskStrategy", "error.field.mask-required"),
                            tuple("policies[1].fieldCode", "error.field.duplicate"),
                            tuple("policies[2].fieldCode", "error.field.unknown"),
                            tuple("policies[3].maskStrategy", "error.field.mask-unexpected"));
                });
        List<FieldPolicyCommand> many = Collections.nCopies(FieldPolicyService.MAX_POLICIES + 1, command("email", FieldReadMode.VISIBLE, null));
        assertThatThrownBy(() -> inAcme(() -> service.replace(7, auditors, many))).isInstanceOfSatisfying(GrantForgeException.class,
                error -> assertThat(error.getFieldIssues()).extracting(FieldIssue::field).containsExactly("policies"));
        assertThat(inAcme(() -> service.list(auditors))).isEmpty();
        assertThatThrownBy(() -> inAcme(() -> service.list(424242L))).isInstanceOfSatisfying(GrantForgeException.class,
                error -> assertThat(error.getErrorCode()).isEqualTo(CommonErrorCode.NOT_FOUND));
        assertThatThrownBy(() -> inAcme(() -> service.replace(7, 424242L, List.of()))).isInstanceOf(GrantForgeException.class);
    }
}

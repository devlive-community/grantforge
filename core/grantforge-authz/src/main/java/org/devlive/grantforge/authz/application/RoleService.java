// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.authz.application;

import org.devlive.grantforge.audit.application.AuditLog;
import org.devlive.grantforge.audit.application.AuditRecord;
import org.devlive.grantforge.audit.domain.AuditAction;
import org.devlive.grantforge.audit.domain.AuditOutcome;
import org.devlive.grantforge.authz.domain.Role;
import org.devlive.grantforge.authz.domain.RoleAssignmentRepository;
import org.devlive.grantforge.authz.domain.RoleGrantRepository;
import org.devlive.grantforge.authz.domain.RoleRepository;
import org.devlive.grantforge.authz.domain.RoleType;
import org.devlive.grantforge.common.error.CommonErrorCode;
import org.devlive.grantforge.common.error.GrantForgeException;
import org.devlive.grantforge.persistence.tenant.TenantContext;
import org.jspecify.annotations.Nullable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Supplier;

import static java.util.Objects.requireNonNull;

/**
 * The roles of the bound tenant. Who may manage them is a matter of permissions, which the API checks. System roles cannot be changed, disabled or deleted, only copied. Every method must be
 * called with the actor's tenant bound.
 */
@Service
public final class RoleService
{
    private final RoleRepository roles;
    private final RoleAssignmentRepository assignments;
    private final RoleGrantRepository grants;
    private final AuditLog audit;
    private final TransactionTemplate transactions;

    /**
     * Creates the service.
     *
     * @param roles roles of the bound tenant
     * @param assignments assignments, removed with their role
     * @param grants grants, copied with their role and removed with it
     * @param audit records every change
     * @param transactionManager opens transactions
     */
    public RoleService(RoleRepository roles, RoleAssignmentRepository assignments, RoleGrantRepository grants,
            AuditLog audit, PlatformTransactionManager transactionManager)
    {
        this.grants = requireNonNull(grants, "grants");
        this.roles = requireNonNull(roles, "roles");
        this.assignments = requireNonNull(assignments, "assignments");
        this.audit = requireNonNull(audit, "audit");
        this.transactions = new TransactionTemplate(requireNonNull(transactionManager, "transactionManager"));
    }

    /**
     * Lists the roles whose code or name contains a text, system roles first, then by name.
     *
     * @param actorId the account asking
     * @param text the text, or {@code null} for every role
     * @return the roles
     */
    public List<RoleView> list(long actorId, @Nullable String text)
    {
        return requireNonNull(transactions.execute(status -> roles.search(pattern(text)).stream().map(RoleView::from).toList()));
    }

    /**
     * Returns one role.
     *
     * @param actorId the account asking
     * @param id the role
     * @return the role
     * @throws GrantForgeException with {@link CommonErrorCode#NOT_FOUND}
     */
    public RoleView find(long actorId, long id)
    {
        return RoleView.from(requireNonNull(transactions.execute(status -> require(id))));
    }

    /**
     * Creates an enabled custom role.
     *
     * @param actorId the account asking
     * @param code the code, unique in the tenant
     * @param name the name
     * @param description an optional explanation
     * @return the role
     * @throws GrantForgeException with {@link AuthzErrorCode#ROLE_CODE_TAKEN} or
     *         {@link CommonErrorCode#BAD_REQUEST}
     */
    public RoleView create(long actorId, @Nullable String code, @Nullable String name, @Nullable String description)
    {
        Role role = write(() -> {
            Role created = Catalog.valid(() -> Role.create(String.valueOf(code), String.valueOf(name), description));
            requireFreeCode(created.getCode(), null);
            return roles.saveAndFlush(created);
        });
        record(AuditAction.ROLE_CREATED, actorId, role, role.getCode());
        return RoleView.from(role);
    }

    /**
     * Changes the code, name and description of a custom role.
     *
     * @param actorId the account asking
     * @param id the role
     * @param code the new code
     * @param name the new name
     * @param description the new description; blank removes it
     * @return the role
     * @throws GrantForgeException with {@link CommonErrorCode#NOT_FOUND},
     *         {@link AuthzErrorCode#ROLE_PROTECTED}, {@link AuthzErrorCode#ROLE_CODE_TAKEN} or
     *         {@link CommonErrorCode#BAD_REQUEST}
     */
    public RoleView update(long actorId, long id, @Nullable String code, @Nullable String name, @Nullable String description)
    {
        Role role = write(() -> {
            Role found = requireCustom(id);
            requireFreeCode(String.valueOf(code).trim().toLowerCase(Locale.ROOT), id);
            Catalog.valid(() -> {
                found.change(String.valueOf(code), String.valueOf(name), description);
                return found;
            });
            return roles.saveAndFlush(found);
        });
        record(AuditAction.ROLE_UPDATED, actorId, role, role.getCode());
        return RoleView.from(role);
    }

    /**
     * Creates an enabled custom role as a copy of another, with its grants. A system role's copy starts without
     * grants: system roles allow whole modules without grants of their own.
     *
     * @param actorId the account asking
     * @param id the role to copy
     * @param code the copy's code
     * @param name the copy's name
     * @return the copy
     * @throws GrantForgeException with {@link CommonErrorCode#NOT_FOUND},
     *         {@link AuthzErrorCode#ROLE_CODE_TAKEN} or {@link CommonErrorCode#BAD_REQUEST}
     */
    public RoleView copy(long actorId, long id, @Nullable String code, @Nullable String name)
    {
        Copy done = write(() -> {
            Role original = require(id);
            Role copy = Catalog.valid(() -> Role.create(String.valueOf(code), String.valueOf(name), original.getDescription()));
            requireFreeCode(copy.getCode(), null);
            Role saved = roles.saveAndFlush(copy);
            grants.saveAll(grants.findByRoleId(id).stream().map(grant -> grant.copyTo(saved.requireId(), actorId)).toList());
            return new Copy(original, saved);
        });
        record(AuditAction.ROLE_COPIED, actorId, done.original(), Long.toString(done.copy().requireId()));
        return RoleView.from(done.copy());
    }

    /**
     * Enables or disables a custom role.
     *
     * @param actorId the account asking
     * @param id the role
     * @param enabled whether it grants anything
     * @return the role
     * @throws GrantForgeException with {@link CommonErrorCode#NOT_FOUND} or
     *         {@link AuthzErrorCode#ROLE_PROTECTED}
     */
    public RoleView enable(long actorId, long id, boolean enabled)
    {
        Role role = write(() -> {
            Role found = requireCustom(id);
            found.enable(enabled);
            return roles.saveAndFlush(found);
        });
        record(enabled ? AuditAction.ROLE_ENABLED : AuditAction.ROLE_DISABLED, actorId, role, role.getCode());
        return RoleView.from(role);
    }

    /**
     * Deletes a custom role with its assignments and grants.
     *
     * @param actorId the account asking
     * @param id the role
     * @throws GrantForgeException with {@link CommonErrorCode#NOT_FOUND} or
     *         {@link AuthzErrorCode#ROLE_PROTECTED}
     */
    public void delete(long actorId, long id)
    {
        Role role = write(() -> {
            Role found = requireCustom(id);
            assignments.removeRole(id);
            grants.removeRole(id);
            roles.delete(found);
            return found;
        });
        record(AuditAction.ROLE_DELETED, actorId, role, role.getCode());
    }

    private <T> T write(Supplier<T> change)
    {
        try {
            return requireNonNull(transactions.execute(status -> change.get()));
        }
        catch (DataIntegrityViolationException race) {
            throw new GrantForgeException(CommonErrorCode.CONFLICT, "role changed concurrently", race);
        }
    }

    private Role require(long id)
    {
        return roles.findById(id).orElseThrow(() -> new GrantForgeException(CommonErrorCode.NOT_FOUND, "no role " + id));
    }

    private Role requireCustom(long id)
    {
        Role role = require(id);
        if (role.getType() == RoleType.SYSTEM) {
            throw new GrantForgeException(AuthzErrorCode.ROLE_PROTECTED, "role " + id + " is a system role");
        }
        return role;
    }

    private void requireFreeCode(String code, @Nullable Long except)
    {
        roles.findByCode(code).filter(other -> !Objects.equals(other.getId(), except)).ifPresent(other -> {
            throw new GrantForgeException(AuthzErrorCode.ROLE_CODE_TAKEN, "role code taken", code);
        });
    }

    /** A lowercase {@code LIKE} pattern of user input; its own wildcards are dropped. */
    private static String pattern(@Nullable String text)
    {
        String needle = text == null ? null : text.strip().replace("%", "").replace("_", "");
        return needle == null || needle.isEmpty() ? "%" : "%" + needle.toLowerCase(Locale.ROOT) + "%";
    }

    private void record(AuditAction action, long actorId, Role role, String reason)
    {
        audit.record(new AuditRecord(action, AuditOutcome.SUCCESS, TenantContext.requireTenantId(), actorId, null,
                Long.toString(role.requireId()), reason));
    }

    /** A role and its new copy. */
    private record Copy(Role original, Role copy)
    {
    }
}

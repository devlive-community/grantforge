// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.identity.application;

import org.devlive.grantforge.audit.application.AuditLog;
import org.devlive.grantforge.audit.application.AuditRecord;
import org.devlive.grantforge.audit.domain.AuditAction;
import org.devlive.grantforge.audit.domain.AuditOutcome;
import org.devlive.grantforge.common.error.CommonErrorCode;
import org.devlive.grantforge.common.error.GrantForgeException;
import org.devlive.grantforge.identity.domain.OrgUnit;
import org.devlive.grantforge.identity.domain.OrgUnitRepository;
import org.devlive.grantforge.identity.domain.UserAccount;
import org.devlive.grantforge.identity.domain.UserAccountRepository;
import org.devlive.grantforge.persistence.tenant.TenantContext;
import org.jspecify.annotations.Nullable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Supplier;

import static java.util.Objects.requireNonNull;

/**
 * The organization tree of the bound tenant. Every signed-in user may read it; until roles exist only system
 * accounts (tenant administrators) change it. Every method must be called with the actor's tenant bound.
 */
@Service
public final class OrgService
{
    private final OrgUnitRepository units;
    private final UserAccountRepository accounts;
    private final AuditLog audit;
    private final TransactionTemplate transactions;

    /**
     * Creates the service.
     *
     * @param units departments
     * @param accounts user accounts, to check the actor
     * @param audit records every change
     * @param transactionManager opens transactions
     */
    public OrgService(OrgUnitRepository units, UserAccountRepository accounts, AuditLog audit,
            PlatformTransactionManager transactionManager)
    {
        this.units = requireNonNull(units, "units");
        this.accounts = requireNonNull(accounts, "accounts");
        this.audit = requireNonNull(audit, "audit");
        this.transactions = new TransactionTemplate(requireNonNull(transactionManager, "transactionManager"));
    }

    /**
     * Returns the whole tree, parents before children and siblings in order.
     *
     * @return the departments
     */
    public List<OrgUnitView> tree()
    {
        return requireNonNull(transactions.execute(status -> units.findTree().stream().map(OrgUnitView::from).toList()));
    }

    /**
     * Creates a department as the last child of a parent, or as the last root.
     *
     * @param actorId the account asking
     * @param parentId the parent, or {@code null} for a root
     * @param code the code, unique in the tenant
     * @param name the name
     * @return the new department
     * @throws GrantForgeException with {@link CommonErrorCode#FORBIDDEN}, {@link CommonErrorCode#NOT_FOUND} for an
     *         unknown parent, {@link IdentityErrorCode#ORG_CODE_TAKEN}, {@link IdentityErrorCode#ORG_TOO_DEEP} or
     *         {@link CommonErrorCode#BAD_REQUEST}
     */
    public OrgUnitView create(long actorId, @Nullable Long parentId, @Nullable String code, @Nullable String name)
    {
        OrgUnit unit = write(actorId, () -> {
            OrgUnit parent = parentId == null ? null : require(parentId);
            if (parent != null && parent.getDepth() >= OrgUnit.MAX_DEPTH) {
                throw tooDeep();
            }
            OrgUnit created = valid(() -> OrgUnit.create(parent, String.valueOf(code), String.valueOf(name),
                    units.findChildren(parentId).size()));
            requireFreeCode(created.getCode(), null);
            return units.saveAndFlush(created);
        });
        record(AuditAction.ORG_UNIT_CREATED, actorId, unit);
        return OrgUnitView.from(unit);
    }

    /**
     * Changes the code and name of a department.
     *
     * @param actorId the account asking
     * @param id the department
     * @param code the new code
     * @param name the new name
     * @return the department
     * @throws GrantForgeException with {@link CommonErrorCode#FORBIDDEN}, {@link CommonErrorCode#NOT_FOUND},
     *         {@link IdentityErrorCode#ORG_CODE_TAKEN} or {@link CommonErrorCode#BAD_REQUEST}
     */
    public OrgUnitView update(long actorId, long id, @Nullable String code, @Nullable String name)
    {
        OrgUnit unit = write(actorId, () -> {
            OrgUnit found = require(id);
            requireFreeCode(String.valueOf(code).trim().toLowerCase(Locale.ROOT), id);
            valid(() -> {
                found.rename(String.valueOf(code), String.valueOf(name));
                return found;
            });
            return units.saveAndFlush(found);
        });
        record(AuditAction.ORG_UNIT_UPDATED, actorId, unit);
        return OrgUnitView.from(unit);
    }

    /**
     * Moves a department, with everything below it, under another parent (or to the roots) at a position among
     * the new siblings.
     *
     * @param actorId the account asking
     * @param id the department
     * @param parentId the new parent, or {@code null} to make it a root
     * @param position the 0-based position among the new siblings; clamped to the valid range
     * @return the department
     * @throws GrantForgeException with {@link CommonErrorCode#FORBIDDEN}, {@link CommonErrorCode#NOT_FOUND},
     *         {@link IdentityErrorCode#ORG_MOVE_CYCLE} or {@link IdentityErrorCode#ORG_TOO_DEEP}
     */
    public OrgUnitView move(long actorId, long id, @Nullable Long parentId, int position)
    {
        OrgUnit unit = write(actorId, () -> {
            OrgUnit moving = require(id);
            OrgUnit parent = parentId == null ? null : require(parentId);
            if (!Objects.equals(moving.getParentId(), parentId)) {
                reparent(moving, parent);
            }
            // The bulk update cleared the persistence context; work on fresh copies of the new siblings.
            List<OrgUnit> siblings = new ArrayList<>(units.findChildren(parentId));
            OrgUnit moved = siblings.stream().filter(sibling -> sibling.requireId() == id).findFirst().orElseThrow();
            siblings.remove(moved);
            siblings.add(Math.max(0, Math.min(position, siblings.size())), moved);
            for (int i = 0; i < siblings.size(); i++) {
                siblings.get(i).placeAt(i);
            }
            return moved;
        });
        record(AuditAction.ORG_UNIT_MOVED, actorId, unit);
        return OrgUnitView.from(unit);
    }

    /**
     * Deletes a department without sub-departments.
     *
     * @param actorId the account asking
     * @param id the department
     * @throws GrantForgeException with {@link CommonErrorCode#FORBIDDEN}, {@link CommonErrorCode#NOT_FOUND} or
     *         {@link IdentityErrorCode#ORG_NOT_EMPTY}
     */
    public void delete(long actorId, long id)
    {
        OrgUnit unit = write(actorId, () -> {
            OrgUnit found = require(id);
            if (units.existsByParentId(id)) {
                throw new GrantForgeException(IdentityErrorCode.ORG_NOT_EMPTY, "department " + id + " has children");
            }
            units.delete(found);
            return found;
        });
        record(AuditAction.ORG_UNIT_DELETED, actorId, unit);
    }

    private void reparent(OrgUnit moving, @Nullable OrgUnit parent)
    {
        // Moving below itself or a descendant would detach the subtree into a cycle.
        if (parent != null && moving.contains(parent)) {
            throw new GrantForgeException(IdentityErrorCode.ORG_MOVE_CYCLE, "cannot move " + moving.requireId()
                    + " below " + parent.requireId());
        }
        String oldPrefix = moving.getPath();
        String newPrefix = (parent == null ? "/" : parent.getPath()) + moving.requireId() + "/";
        int shift = (parent == null ? 0 : parent.getDepth() + 1) - moving.getDepth();
        if (units.maxDepthBelow(oldPrefix + "%") + shift > OrgUnit.MAX_DEPTH) {
            throw tooDeep();
        }
        units.reparent(moving.requireId(), parent == null ? null : parent.requireId());
        units.moveSubtree(oldPrefix, oldPrefix + "%", newPrefix, oldPrefix.length() + 1, shift);
    }

    private OrgUnit write(long actorId, Supplier<OrgUnit> change)
    {
        requireAdministrator(actorId);
        try {
            return requireNonNull(transactions.execute(status -> change.get()));
        }
        catch (DataIntegrityViolationException race) {
            throw new GrantForgeException(CommonErrorCode.CONFLICT, "department changed concurrently", race);
        }
    }

    private void requireAdministrator(long actorId)
    {
        boolean administrator = Boolean.TRUE.equals(transactions.execute(status -> accounts.findById(actorId)
                .map(UserAccount::isSystemAccount).orElse(false)));
        if (!administrator) {
            throw new GrantForgeException(CommonErrorCode.FORBIDDEN, "account " + actorId + " may not change departments");
        }
    }

    private void requireFreeCode(String code, @Nullable Long except)
    {
        units.findByCode(code).filter(other -> !Objects.equals(other.getId(), except)).ifPresent(other -> {
            throw new GrantForgeException(IdentityErrorCode.ORG_CODE_TAKEN, "department code taken", code);
        });
    }

    private OrgUnit require(long id)
    {
        return units.findById(id)
                .orElseThrow(() -> new GrantForgeException(CommonErrorCode.NOT_FOUND, "no department " + id));
    }

    private static GrantForgeException tooDeep()
    {
        return new GrantForgeException(IdentityErrorCode.ORG_TOO_DEEP, "too deep", OrgUnit.MAX_DEPTH + 1);
    }

    private static OrgUnit valid(Supplier<OrgUnit> build)
    {
        try {
            return build.get();
        }
        catch (IllegalArgumentException invalid) {
            throw new GrantForgeException(CommonErrorCode.BAD_REQUEST, String.valueOf(invalid.getMessage()), invalid);
        }
    }

    private void record(AuditAction action, long actorId, OrgUnit unit)
    {
        audit.record(new AuditRecord(action, AuditOutcome.SUCCESS, TenantContext.requireTenantId(), actorId, null,
                Long.toString(unit.requireId()), null));
    }
}

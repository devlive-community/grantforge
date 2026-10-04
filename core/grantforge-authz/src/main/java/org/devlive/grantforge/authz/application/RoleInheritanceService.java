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
import org.devlive.grantforge.authz.domain.RoleHierarchy;
import org.devlive.grantforge.authz.domain.RoleParent;
import org.devlive.grantforge.authz.domain.RoleParentRepository;
import org.devlive.grantforge.authz.domain.RoleRepository;
import org.devlive.grantforge.authz.domain.RoleType;
import org.devlive.grantforge.common.error.CommonErrorCode;
import org.devlive.grantforge.common.error.GrantForgeException;
import org.devlive.grantforge.persistence.tenant.TenantContext;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static java.util.Objects.requireNonNull;

/**
 * Which roles of the bound tenant inherit from which. A role inherits everything its parents allow and deny, and
 * what they inherit in turn, as long as they are enabled. System roles inherit from nothing but may be inherited
 * from. Nobody lets a role inherit more than they have themselves, and the links never form a cycle. Every method
 * must be called with the actor's tenant bound.
 */
@Service
public final class RoleInheritanceService
{
    /** Most roles one role inherits from directly. */
    static final int MAX_PARENTS = 20;

    private final RoleRepository roles;
    private final RoleParentRepository parents;
    private final AuthorizationEvaluator evaluator;
    private final AuditLog audit;
    private final TransactionTemplate transactions;

    /**
     * Creates the service.
     *
     * @param roles roles of the bound tenant
     * @param parents inheritance links of the bound tenant
     * @param evaluator tells whether the actor has what a parent role allows
     * @param audit records every change
     * @param transactionManager opens transactions
     */
    public RoleInheritanceService(RoleRepository roles, RoleParentRepository parents, AuthorizationEvaluator evaluator,
            AuditLog audit, PlatformTransactionManager transactionManager)
    {
        this.roles = requireNonNull(roles, "roles");
        this.parents = requireNonNull(parents, "parents");
        this.evaluator = requireNonNull(evaluator, "evaluator");
        this.audit = requireNonNull(audit, "audit");
        this.transactions = new TransactionTemplate(requireNonNull(transactionManager, "transactionManager"));
    }

    /**
     * Returns every inheritance link of the tenant, for drawing them.
     *
     * @return the links as pairs of role and parent
     */
    public List<Link> links()
    {
        return requireNonNull(transactions.execute(status -> parents.findAll().stream()
                .map(link -> new Link(link.getRoleId(), link.getParentId())).toList()));
    }

    /**
     * Returns how a role sits in the inheritance graph.
     *
     * @param roleId the role
     * @return its parents, ancestors and descendants
     * @throws GrantForgeException with {@link CommonErrorCode#NOT_FOUND} for an unknown role
     */
    public RoleInheritance of(long roleId)
    {
        return requireNonNull(transactions.execute(status -> view(require(roleId))));
    }

    /**
     * Replaces the roles a custom role inherits from directly.
     *
     * @param actorId the account asking
     * @param roleId the role
     * @param parentIds the roles to inherit from, at most {@value #MAX_PARENTS}
     * @return how the role now sits in the graph
     * @throws GrantForgeException with {@link CommonErrorCode#NOT_FOUND} for an unknown role,
     *         {@link AuthzErrorCode#ROLE_PROTECTED} for a system role, {@link CommonErrorCode#BAD_REQUEST} for too
     *         many parents, {@link AuthzErrorCode#ROLE_INHERITANCE_CYCLE} if a parent inherits from the role, or
     *         {@link AuthzErrorCode#ROLE_EXCEEDS_ACTOR} if a new parent allows more than the actor has
     */
    public RoleInheritance setParents(long actorId, long roleId, Collection<Long> parentIds)
    {
        Set<Long> wanted = new LinkedHashSet<>(parentIds);
        if (wanted.size() > MAX_PARENTS) {
            throw new GrantForgeException(CommonErrorCode.BAD_REQUEST, "at most " + MAX_PARENTS + " parents per role");
        }
        Changed changed = write(() -> {
            Role role = require(roleId);
            if (role.getType() == RoleType.SYSTEM) {
                throw new GrantForgeException(AuthzErrorCode.ROLE_PROTECTED, "role " + roleId + " is a system role");
            }
            List<RoleParent> current = parents.findByRoleId(roleId);
            Set<Long> existing = current.stream().map(RoleParent::getParentId).collect(Collectors.toSet());
            // Cycles are checked against the graph without the role's current parents, which are being replaced.
            RoleHierarchy others = new RoleHierarchy(parents.findAll().stream().filter(link -> link.getRoleId() != roleId).toList());
            Map<Long, Role> found = roles.findAllById(wanted).stream().collect(Collectors.toMap(Role::requireId, Function.identity()));
            for (long parentId : wanted) {
                Role parent = found.get(parentId);
                if (parent == null) {
                    throw new GrantForgeException(CommonErrorCode.NOT_FOUND, "no role " + parentId);
                }
                if (others.wouldCycle(roleId, parentId)) {
                    throw new GrantForgeException(AuthzErrorCode.ROLE_INHERITANCE_CYCLE, roleId + " -> " + parentId,
                            parent.getCode());
                }
                if (!existing.contains(parentId) && !evaluator.covers(actorId, RoleView.from(parent))) {
                    throw new GrantForgeException(AuthzErrorCode.ROLE_EXCEEDS_ACTOR, "role " + parent.getCode()
                            + " exceeds account " + actorId);
                }
            }
            parents.deleteAll(current.stream().filter(link -> !wanted.contains(link.getParentId())).toList());
            parents.saveAll(wanted.stream().filter(parentId -> !existing.contains(parentId))
                    .map(parentId -> RoleParent.of(roleId, parentId)).toList());
            parents.flush();
            Changed made = new Changed(view(role), !existing.equals(wanted));
            if (made.any()) {
                // In the same transaction: the change and its event commit or roll back together.
                audit.recordWithChange(new AuditRecord(AuditAction.ROLE_PARENTS_CHANGED, AuditOutcome.SUCCESS,
                        TenantContext.requireTenantId(), actorId, null, Long.toString(roleId), String.join(",", made.inheritance()
                        .parents().stream().map(RoleView::code).toList())));
            }
            return made;
        });
        return changed.inheritance();
    }

    private RoleInheritance view(Role role)
    {
        RoleHierarchy hierarchy = new RoleHierarchy(parents.findAll());
        long id = role.requireId();
        Map<Long, Integer> ancestors = hierarchy.ancestors(id);
        Map<Long, Integer> descendants = hierarchy.descendants(id);
        Set<Long> related = new LinkedHashSet<>(ancestors.keySet());
        related.addAll(descendants.keySet());
        Map<Long, RoleView> views = roles.findAllById(related).stream().map(RoleView::from)
                .collect(Collectors.toMap(RoleView::id, Function.identity()));
        return new RoleInheritance(RoleView.from(role),
                hierarchy.parentsOf(id).stream().map(views::get).filter(Objects::nonNull).toList(),
                related(ancestors, views), related(descendants, views));
    }

    private static List<RoleInheritance.Related> related(Map<Long, Integer> distances, Map<Long, RoleView> views)
    {
        List<RoleInheritance.Related> found = new ArrayList<>();
        distances.forEach((id, distance) -> {
            RoleView view = views.get(id);
            if (view != null) {
                found.add(new RoleInheritance.Related(view, distance));
            }
        });
        return found;
    }

    private Role require(long id)
    {
        return roles.findById(id).orElseThrow(() -> new GrantForgeException(CommonErrorCode.NOT_FOUND, "no role " + id));
    }

    private <T> T write(Supplier<T> change)
    {
        try {
            return requireNonNull(transactions.execute(status -> change.get()));
        }
        catch (DataIntegrityViolationException race) {
            throw new GrantForgeException(CommonErrorCode.CONFLICT, "role inheritance changed concurrently", race);
        }
    }

    /**
     * One inheritance link.
     *
     * @param roleId the inheriting role
     * @param parentId the role it inherits from
     */
    public record Link(long roleId, long parentId)
    {
    }

    /** The result of a change and whether anything changed. */
    private record Changed(RoleInheritance inheritance, boolean any)
    {
    }
}

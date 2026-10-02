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
import org.devlive.grantforge.common.lang.Strings;
import org.devlive.grantforge.common.page.PageQuery;
import org.devlive.grantforge.common.page.PageResult;
import org.devlive.grantforge.identity.domain.GroupMember;
import org.devlive.grantforge.identity.domain.GroupMemberRepository;
import org.devlive.grantforge.identity.domain.GroupRow;
import org.devlive.grantforge.identity.domain.MemberRow;
import org.devlive.grantforge.identity.domain.UserAccount;
import org.devlive.grantforge.identity.domain.UserAccountRepository;
import org.devlive.grantforge.identity.domain.UserGroup;
import org.devlive.grantforge.identity.domain.UserGroupRepository;
import org.devlive.grantforge.persistence.authz.AuthorizationChanges;
import org.devlive.grantforge.persistence.query.IdOrder;
import org.devlive.grantforge.persistence.query.InClauseBatcher;
import org.devlive.grantforge.persistence.secured.DataAction;
import org.devlive.grantforge.persistence.secured.RowScopes;
import org.devlive.grantforge.persistence.tenant.TenantContext;
import org.jspecify.annotations.Nullable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;

import static java.util.Objects.requireNonNull;

/**
 * User groups of the bound tenant and their members. Callers need the matching permission, which the API checks. Members are added and removed in batches; adding an account that already belongs, or
 * removing one that does not, changes nothing. Actors only see and change the groups their data scope covers, and
 * only add accounts they may see; the others look as if they did not exist. Every method must be called with the actor's
 * tenant bound.
 */
@Service
public final class GroupService
{
    /** Most accounts added or removed in one call. */
    public static final int MAX_BATCH = 500;

    private static final Sort BY_NAME = Sort.by("name", "id");

    private final UserGroupRepository groups;
    private final AuthorizationChanges changes;
    private final GroupMemberRepository members;
    private final UserAccountRepository accounts;
    private final AuditLog audit;
    private final TransactionTemplate transactions;
    private final ApplicationEventPublisher events;
    private final RowScopes scopes;

    /**
     * Creates the service.
     *
     * @param groups user groups
     * @param members group memberships
     * @param accounts user accounts
     * @param audit records every change
     * @param transactionManager opens transactions
     * @param events announces deletions
     * @param changes notes changes of what permissions are worked out from
     * @param scopes the groups and accounts each actor may use
     */
    public GroupService(UserGroupRepository groups, GroupMemberRepository members, UserAccountRepository accounts,
            AuditLog audit, PlatformTransactionManager transactionManager,
            ApplicationEventPublisher events, AuthorizationChanges changes, RowScopes scopes)
    {
        this.scopes = requireNonNull(scopes, "scopes");
        this.changes = requireNonNull(changes, "changes");
        this.events = requireNonNull(events, "events");
        this.groups = requireNonNull(groups, "groups");
        this.members = requireNonNull(members, "members");
        this.accounts = requireNonNull(accounts, "accounts");
        this.audit = requireNonNull(audit, "audit");
        this.transactions = new TransactionTemplate(requireNonNull(transactionManager, "transactionManager"));
    }

    /**
     * Lists groups whose code or name contains a text, by name, with their member counts.
     *
     * @param actorId the account asking
     * @param text the text to look for, or {@code null} for every group
     * @param page the page
     * @return the groups
     */
    public PageResult<GroupRow> list(long actorId, @Nullable String text, PageQuery page)
    {
        String pattern = pattern(text);
        Specification<UserGroup> matching = (root, query, builder) -> builder.or(builder.like(root.get("code"), pattern),
                builder.like(builder.lower(root.get("name")), pattern));
        return requireNonNull(transactions.execute(status -> {
            Page<UserGroup> found = groups.findAll(scopes.scope(actorId, UserGroup.class, DataAction.READ).and(matching),
                    PageRequest.of(page.page() - 1, page.size(), BY_NAME));
            List<Long> ids = found.stream().map(UserGroup::requireId).toList();
            List<GroupRow> rows = ids.isEmpty() ? List.of() : IdOrder.arrange(ids, groups.rows(ids), GroupRow::id);
            return new PageResult<>(rows, page.page(), page.size(), found.getTotalElements());
        }));
    }

    /**
     * Creates a group.
     *
     * @param actorId the account asking
     * @param code the code, unique in the tenant
     * @param name the name
     * @param description the description, if any
     * @return the group
     * @throws GrantForgeException with {@link IdentityErrorCode#GROUP_CODE_TAKEN}
     *         or {@link CommonErrorCode#BAD_REQUEST}
     */
    public GroupRow create(long actorId, @Nullable String code, @Nullable String name, @Nullable String description)
    {
        UserGroup group = write(() -> {
            UserGroup created = valid(() -> UserGroup.create(String.valueOf(code), String.valueOf(name), description));
            requireFreeCode(created.getCode(), null);
            return groups.saveAndFlush(created);
        });
        record(AuditAction.GROUP_CREATED, actorId, group.requireId(), null);
        return row(group, 0);
    }

    /**
     * Changes a group's code, name and description.
     *
     * @param actorId the account asking
     * @param groupId the group
     * @param code the new code
     * @param name the new name
     * @param description the new description; blank clears it
     * @return the group
     * @throws GrantForgeException with {@link CommonErrorCode#NOT_FOUND},
     *         {@link IdentityErrorCode#GROUP_CODE_TAKEN} or {@link CommonErrorCode#BAD_REQUEST}
     */
    public GroupRow update(long actorId, long groupId, @Nullable String code, @Nullable String name,
            @Nullable String description)
    {
        UserGroup group = write(() -> {
            UserGroup found = require(actorId, groupId, DataAction.UPDATE);
            requireFreeCode(String.valueOf(code).trim().toLowerCase(Locale.ROOT), groupId);
            valid(() -> {
                found.change(String.valueOf(code), String.valueOf(name), description);
                return found;
            });
            return groups.saveAndFlush(found);
        });
        record(AuditAction.GROUP_UPDATED, actorId, groupId, null);
        return row(group, memberCount(groupId));
    }

    /**
     * Deletes a group with its memberships; the accounts stay.
     *
     * @param actorId the account asking
     * @param groupId the group
     * @throws GrantForgeException with {@link CommonErrorCode#NOT_FOUND}
     */
    public void delete(long actorId, long groupId)
    {
        transactions.executeWithoutResult(status -> {
            UserGroup group = require(actorId, groupId, DataAction.DELETE);
            members.removeAll(groupId);
            changes.currentTenant();
            groups.delete(group);
        });
        events.publishEvent(new IdentityDeleted(IdentityDeleted.Kind.GROUP, groupId));
        record(AuditAction.GROUP_DELETED, actorId, groupId, null);
    }

    /**
     * Lists a group's members whose login name, display name or e-mail address contains a text.
     *
     * @param actorId the account asking
     * @param groupId the group
     * @param text the text to look for, or {@code null} for every member
     * @param page the page
     * @return the members, by login name
     * @throws GrantForgeException with {@link CommonErrorCode#NOT_FOUND}
     */
    public PageResult<MemberRow> members(long actorId, long groupId, @Nullable String text, PageQuery page)
    {
        Page<MemberRow> found = requireNonNull(transactions.execute(status -> {
            require(actorId, groupId, DataAction.READ);
            return members.findMembers(groupId, pattern(text), PageRequest.of(page.page() - 1, page.size()));
        }));
        return new PageResult<>(found.getContent(), page.page(), page.size(), found.getTotalElements());
    }

    /**
     * Adds accounts to a group; accounts that already belong are skipped.
     *
     * @param actorId the account asking
     * @param groupId the group
     * @param accountIds the accounts, at most {@value #MAX_BATCH}
     * @return how many accounts joined
     * @throws GrantForgeException with {@link CommonErrorCode#NOT_FOUND} for an
     *         unknown group or account, or {@link CommonErrorCode#BAD_REQUEST} for too many accounts
     */
    public int addMembers(long actorId, long groupId, Collection<Long> accountIds)
    {
        Set<Long> wanted = batch(accountIds);
        int added = requireNonNull(write(() -> {
            require(actorId, groupId, DataAction.UPDATE);
            // Only accounts the actor may see can join, as if the others did not exist.
            Specification<UserAccount> visible = scopes.scope(actorId, UserAccount.class, DataAction.READ);
            Set<Long> known = new HashSet<>(InClauseBatcher.query(wanted, batch -> accounts.findAllWithin(batch, visible))
                    .stream().map(UserAccount::requireId).toList());
            wanted.stream().filter(id -> !known.contains(id)).findFirst().ifPresent(id -> {
                throw new GrantForgeException(CommonErrorCode.NOT_FOUND, "no account " + id);
            });
            Set<Long> present = new HashSet<>(InClauseBatcher.query(wanted, batch -> members.findMemberIds(groupId, batch)));
            List<GroupMember> joining = wanted.stream().filter(id -> !present.contains(id))
                    .map(id -> GroupMember.of(groupId, id)).toList();
            members.saveAll(joining);
            return joining.size();
        }));
        if (added > 0) {
            record(AuditAction.GROUP_MEMBERS_ADDED, actorId, groupId, Integer.toString(added));
        }
        return added;
    }

    /**
     * Removes accounts from a group; accounts that do not belong are skipped.
     *
     * @param actorId the account asking
     * @param groupId the group
     * @param accountIds the accounts, at most {@value #MAX_BATCH}
     * @return how many accounts left
     * @throws GrantForgeException with {@link CommonErrorCode#NOT_FOUND} for an
     *         unknown group, or {@link CommonErrorCode#BAD_REQUEST} for too many accounts
     */
    public int removeMembers(long actorId, long groupId, Collection<Long> accountIds)
    {
        Set<Long> leaving = batch(accountIds);
        int removed = requireNonNull(transactions.execute(status -> {
            require(actorId, groupId, DataAction.UPDATE);
            changes.currentTenant();
            return InClauseBatcher.query(leaving, batch -> List.of(members.removeMembers(groupId, batch))).stream()
                    .mapToInt(Integer::intValue).sum();
        }));
        if (removed > 0) {
            record(AuditAction.GROUP_MEMBERS_REMOVED, actorId, groupId, Integer.toString(removed));
        }
        return removed;
    }

    private static Set<Long> batch(Collection<Long> accountIds)
    {
        Set<Long> ids = new LinkedHashSet<>(requireNonNull(accountIds, "accountIds"));
        if (ids.size() > MAX_BATCH) {
            throw new GrantForgeException(CommonErrorCode.BAD_REQUEST, "at most " + MAX_BATCH + " accounts per call");
        }
        return ids;
    }

    private long memberCount(long groupId)
    {
        return requireNonNull(transactions.execute(status ->
                members.findMembers(groupId, "%", PageRequest.of(0, 1)).getTotalElements()));
    }

    private void requireFreeCode(String code, @Nullable Long except)
    {
        groups.findByCode(code).filter(other -> !Objects.equals(other.getId(), except)).ifPresent(other -> {
            throw new GrantForgeException(IdentityErrorCode.GROUP_CODE_TAKEN, "group code taken", code);
        });
    }

    private UserGroup require(long actorId, long groupId, DataAction action)
    {
        return scopes.requireWithin(actorId, groups, UserGroup.class, action, groupId);
    }

    private <T> T write(Supplier<T> change)
    {
        try {
            return requireNonNull(transactions.execute(status -> change.get()));
        }
        catch (DataIntegrityViolationException race) {
            throw new GrantForgeException(CommonErrorCode.CONFLICT, "group changed concurrently", race);
        }
    }

    private static <T> T valid(Supplier<T> build)
    {
        try {
            return build.get();
        }
        catch (IllegalArgumentException invalid) {
            throw new GrantForgeException(CommonErrorCode.BAD_REQUEST, String.valueOf(invalid.getMessage()), invalid);
        }
    }

    /** A lowercase {@code LIKE} pattern of user input; its own wildcards are dropped so they match literally nothing. */
    private static String pattern(@Nullable String text)
    {
        String needle = Strings.blankToNull(text);
        return needle == null ? "%" : "%" + needle.toLowerCase(Locale.ROOT).replace("%", "").replace("_", "") + "%";
    }

    private static GroupRow row(UserGroup group, long memberCount)
    {
        return new GroupRow(group.requireId(), group.getCode(), group.getName(), group.getDescription(), memberCount,
                requireNonNull(group.getCreatedAt(), "createdAt"));
    }

    private void record(AuditAction action, long actorId, long groupId, @Nullable String reason)
    {
        audit.record(new AuditRecord(action, AuditOutcome.SUCCESS, TenantContext.requireTenantId(), actorId, null,
                Long.toString(groupId), reason));
    }
}

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
import org.devlive.grantforge.persistence.query.InClauseBatcher;
import org.devlive.grantforge.persistence.tenant.TenantContext;
import org.jspecify.annotations.Nullable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
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
 * User groups of the bound tenant and their members. Until roles exist only system accounts (tenant
 * administrators) use it. Members are added and removed in batches; adding an account that already belongs, or
 * removing one that does not, changes nothing. Every method must be called with the actor's tenant bound.
 */
@Service
public final class GroupService
{
    /** Most accounts added or removed in one call. */
    public static final int MAX_BATCH = 500;

    private final UserGroupRepository groups;
    private final GroupMemberRepository members;
    private final UserAccountRepository accounts;
    private final AuditLog audit;
    private final TransactionTemplate transactions;

    /**
     * Creates the service.
     *
     * @param groups user groups
     * @param members group memberships
     * @param accounts user accounts
     * @param audit records every change
     * @param transactionManager opens transactions
     */
    public GroupService(UserGroupRepository groups, GroupMemberRepository members, UserAccountRepository accounts,
            AuditLog audit, PlatformTransactionManager transactionManager)
    {
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
     * @throws GrantForgeException with {@link CommonErrorCode#FORBIDDEN} unless the actor is an administrator
     */
    public PageResult<GroupRow> list(long actorId, @Nullable String text, PageQuery page)
    {
        requireAdministrator(actorId);
        Page<GroupRow> found = requireNonNull(transactions.execute(status ->
                groups.search(pattern(text), PageRequest.of(page.page() - 1, page.size()))));
        return new PageResult<>(found.getContent(), page.page(), page.size(), found.getTotalElements());
    }

    /**
     * Creates a group.
     *
     * @param actorId the account asking
     * @param code the code, unique in the tenant
     * @param name the name
     * @param description the description, if any
     * @return the group
     * @throws GrantForgeException with {@link CommonErrorCode#FORBIDDEN}, {@link IdentityErrorCode#GROUP_CODE_TAKEN}
     *         or {@link CommonErrorCode#BAD_REQUEST}
     */
    public GroupRow create(long actorId, @Nullable String code, @Nullable String name, @Nullable String description)
    {
        requireAdministrator(actorId);
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
     * @throws GrantForgeException with {@link CommonErrorCode#FORBIDDEN}, {@link CommonErrorCode#NOT_FOUND},
     *         {@link IdentityErrorCode#GROUP_CODE_TAKEN} or {@link CommonErrorCode#BAD_REQUEST}
     */
    public GroupRow update(long actorId, long groupId, @Nullable String code, @Nullable String name,
            @Nullable String description)
    {
        requireAdministrator(actorId);
        UserGroup group = write(() -> {
            UserGroup found = require(groupId);
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
     * @throws GrantForgeException with {@link CommonErrorCode#FORBIDDEN} or {@link CommonErrorCode#NOT_FOUND}
     */
    public void delete(long actorId, long groupId)
    {
        requireAdministrator(actorId);
        transactions.executeWithoutResult(status -> {
            UserGroup group = require(groupId);
            members.removeAll(groupId);
            groups.delete(group);
        });
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
     * @throws GrantForgeException with {@link CommonErrorCode#FORBIDDEN} or {@link CommonErrorCode#NOT_FOUND}
     */
    public PageResult<MemberRow> members(long actorId, long groupId, @Nullable String text, PageQuery page)
    {
        requireAdministrator(actorId);
        Page<MemberRow> found = requireNonNull(transactions.execute(status -> {
            require(groupId);
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
     * @throws GrantForgeException with {@link CommonErrorCode#FORBIDDEN}, {@link CommonErrorCode#NOT_FOUND} for an
     *         unknown group or account, or {@link CommonErrorCode#BAD_REQUEST} for too many accounts
     */
    public int addMembers(long actorId, long groupId, Collection<Long> accountIds)
    {
        requireAdministrator(actorId);
        Set<Long> wanted = batch(accountIds);
        int added = requireNonNull(write(() -> {
            require(groupId);
            Set<Long> known = new HashSet<>(InClauseBatcher.query(wanted, accounts::findAllById).stream()
                    .map(UserAccount::requireId).toList());
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
     * @throws GrantForgeException with {@link CommonErrorCode#FORBIDDEN}, {@link CommonErrorCode#NOT_FOUND} for an
     *         unknown group, or {@link CommonErrorCode#BAD_REQUEST} for too many accounts
     */
    public int removeMembers(long actorId, long groupId, Collection<Long> accountIds)
    {
        requireAdministrator(actorId);
        Set<Long> leaving = batch(accountIds);
        int removed = requireNonNull(transactions.execute(status -> {
            require(groupId);
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

    private void requireAdministrator(long actorId)
    {
        boolean administrator = Boolean.TRUE.equals(transactions.execute(status -> accounts.findById(actorId)
                .map(UserAccount::isSystemAccount).orElse(false)));
        if (!administrator) {
            throw new GrantForgeException(CommonErrorCode.FORBIDDEN, "account " + actorId + " may not manage groups");
        }
    }

    private void requireFreeCode(String code, @Nullable Long except)
    {
        groups.findByCode(code).filter(other -> !Objects.equals(other.getId(), except)).ifPresent(other -> {
            throw new GrantForgeException(IdentityErrorCode.GROUP_CODE_TAKEN, "group code taken", code);
        });
    }

    private UserGroup require(long groupId)
    {
        return groups.findById(groupId)
                .orElseThrow(() -> new GrantForgeException(CommonErrorCode.NOT_FOUND, "no group " + groupId));
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

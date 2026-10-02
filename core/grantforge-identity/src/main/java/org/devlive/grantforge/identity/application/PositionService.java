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
import org.devlive.grantforge.identity.domain.AccountPositionRepository;
import org.devlive.grantforge.identity.domain.MemberRow;
import org.devlive.grantforge.identity.domain.Position;
import org.devlive.grantforge.identity.domain.PositionRepository;
import org.devlive.grantforge.identity.domain.PositionRow;
import org.devlive.grantforge.persistence.tenant.TenantContext;
import org.jspecify.annotations.Nullable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Supplier;

import static java.util.Objects.requireNonNull;

/**
 * Positions of the bound tenant. Accounts get their positions through the account administration; this service
 * maintains the positions themselves and lists their holders. Callers need the matching permission, which the API checks. Every method must be called with the actor's tenant bound.
 */
@Service
public final class PositionService
{
    private final PositionRepository positions;
    private final AccountPositionRepository holdings;
    private final AuditLog audit;
    private final TransactionTemplate transactions;
    private final ApplicationEventPublisher events;

    /**
     * Creates the service.
     *
     * @param positions positions
     * @param holdings positions held by accounts
     * @param audit records every change
     * @param transactionManager opens transactions
     * @param events announces deletions
     */
    public PositionService(PositionRepository positions, AccountPositionRepository holdings,
            AuditLog audit, PlatformTransactionManager transactionManager,
            ApplicationEventPublisher events)
    {
        this.events = requireNonNull(events, "events");
        this.positions = requireNonNull(positions, "positions");
        this.holdings = requireNonNull(holdings, "holdings");
        this.audit = requireNonNull(audit, "audit");
        this.transactions = new TransactionTemplate(requireNonNull(transactionManager, "transactionManager"));
    }

    /**
     * Lists positions whose code or name contains a text, in list order, with their holder counts.
     *
     * @param actorId the account asking
     * @param text the text to look for, or {@code null} for every position
     * @param page the page
     * @return the positions
     */
    public PageResult<PositionRow> list(long actorId, @Nullable String text, PageQuery page)
    {
        String needle = Strings.blankToNull(text);
        // Wildcards typed by the user are dropped, so "50%" cannot match everything.
        String pattern = needle == null ? "%" : "%" + needle.toLowerCase(Locale.ROOT).replace("%", "").replace("_", "") + "%";
        Page<PositionRow> found = requireNonNull(transactions.execute(status ->
                positions.search(pattern, PageRequest.of(page.page() - 1, page.size()))));
        return new PageResult<>(found.getContent(), page.page(), page.size(), found.getTotalElements());
    }

    /**
     * Returns every position in list order, for choosing an account's positions.
     *
     * @param actorId the account asking
     * @return the positions
     */
    public List<UserPosition> options(long actorId)
    {
        return requireNonNull(transactions.execute(status -> positions.findAllInOrder().stream()
                .map(position -> new UserPosition(position.requireId(), position.getName())).toList()));
    }

    /**
     * Creates a position.
     *
     * @param actorId the account asking
     * @param code the code, unique in the tenant
     * @param name the name
     * @param description the description, if any
     * @param sortOrder where it appears in lists, 0 or more
     * @return the position
     * @throws GrantForgeException with {@link IdentityErrorCode#POSITION_CODE_TAKEN} or {@link CommonErrorCode#BAD_REQUEST}
     */
    public PositionRow create(long actorId, @Nullable String code, @Nullable String name, @Nullable String description,
            int sortOrder)
    {
        Position position = write(() -> {
            Position created = valid(() -> Position.create(String.valueOf(code), String.valueOf(name), description,
                    sortOrder));
            requireFreeCode(created.getCode(), null);
            return positions.saveAndFlush(created);
        });
        record(AuditAction.POSITION_CREATED, actorId, position.requireId());
        return row(position, 0);
    }

    /**
     * Changes a position's details.
     *
     * @param actorId the account asking
     * @param positionId the position
     * @param code the new code
     * @param name the new name
     * @param description the new description; blank clears it
     * @param sortOrder where it appears in lists, 0 or more
     * @return the position
     * @throws GrantForgeException with {@link CommonErrorCode#NOT_FOUND},
     *         {@link IdentityErrorCode#POSITION_CODE_TAKEN} or {@link CommonErrorCode#BAD_REQUEST}
     */
    public PositionRow update(long actorId, long positionId, @Nullable String code, @Nullable String name,
            @Nullable String description, int sortOrder)
    {
        Position position = write(() -> {
            Position found = require(positionId);
            requireFreeCode(String.valueOf(code).trim().toLowerCase(Locale.ROOT), positionId);
            valid(() -> {
                found.change(String.valueOf(code), String.valueOf(name), description, sortOrder);
                return found;
            });
            return positions.saveAndFlush(found);
        });
        record(AuditAction.POSITION_UPDATED, actorId, positionId);
        return row(position, holders(actorId, positionId, new PageQuery(1, 1)).total());
    }

    /**
     * Deletes a position; every account holding it loses it.
     *
     * @param actorId the account asking
     * @param positionId the position
     * @throws GrantForgeException with {@link CommonErrorCode#NOT_FOUND}
     */
    public void delete(long actorId, long positionId)
    {
        transactions.executeWithoutResult(status -> {
            Position position = require(positionId);
            holdings.removePosition(positionId);
            positions.delete(position);
        });
        events.publishEvent(new IdentityDeleted(IdentityDeleted.Kind.POSITION, positionId));
        record(AuditAction.POSITION_DELETED, actorId, positionId);
    }

    /**
     * Lists the accounts holding a position, by login name.
     *
     * @param actorId the account asking
     * @param positionId the position
     * @param page the page
     * @return the holders
     * @throws GrantForgeException with {@link CommonErrorCode#NOT_FOUND}
     */
    public PageResult<MemberRow> holders(long actorId, long positionId, PageQuery page)
    {
        Page<MemberRow> found = requireNonNull(transactions.execute(status -> {
            require(positionId);
            return holdings.findHolders(positionId, PageRequest.of(page.page() - 1, page.size()));
        }));
        return new PageResult<>(found.getContent(), page.page(), page.size(), found.getTotalElements());
    }

    private void requireFreeCode(String code, @Nullable Long except)
    {
        positions.findByCode(code).filter(other -> !Objects.equals(other.getId(), except)).ifPresent(other -> {
            throw new GrantForgeException(IdentityErrorCode.POSITION_CODE_TAKEN, "position code taken", code);
        });
    }

    private Position require(long positionId)
    {
        return positions.findById(positionId)
                .orElseThrow(() -> new GrantForgeException(CommonErrorCode.NOT_FOUND, "no position " + positionId));
    }

    private <T> T write(Supplier<T> change)
    {
        try {
            return requireNonNull(transactions.execute(status -> change.get()));
        }
        catch (DataIntegrityViolationException race) {
            throw new GrantForgeException(CommonErrorCode.CONFLICT, "position changed concurrently", race);
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

    private static PositionRow row(Position position, long holders)
    {
        return new PositionRow(position.requireId(), position.getCode(), position.getName(), position.getDescription(),
                position.getSortOrder(), holders);
    }

    private void record(AuditAction action, long actorId, long positionId)
    {
        audit.record(new AuditRecord(action, AuditOutcome.SUCCESS, TenantContext.requireTenantId(), actorId, null,
                Long.toString(positionId), null));
    }
}

// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.audit.application;

import jakarta.persistence.criteria.Predicate;
import org.devlive.grantforge.audit.domain.AuditEvent;
import org.devlive.grantforge.audit.domain.AuditEventRepository;
import org.devlive.grantforge.common.error.CommonErrorCode;
import org.devlive.grantforge.common.error.GrantForgeException;
import org.devlive.grantforge.common.page.CursorPage;
import org.devlive.grantforge.persistence.secured.DataAction;
import org.devlive.grantforge.persistence.secured.FieldRules;
import org.devlive.grantforge.persistence.secured.FieldView;
import org.devlive.grantforge.persistence.secured.RowScopes;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;

import static java.util.Objects.requireNonNull;

/**
 * Searches and exports the audit events a reader's data policies let them see, newest first, a page at a time with a cursor
 * so pages stay cheap however far back they go. Exports hide and mask the secured fields as the reader sees them. Every
 * method must be called with the reader's tenant bound.
 */
@Service
public final class AuditSearch
{
    /** Most events one page holds. */
    public static final int MAX_PAGE = 200;

    /** Most events one export holds. */
    public static final int MAX_EXPORT = 10_000;

    /** The columns of an export. */
    public static final List<String> COLUMNS = List.of("occurredAt", "action", "outcome", "tenantId", "actorId", "actorName", "targetId",
            "reason", "clientIp", "userAgent", "requestId");

    private static final String ENTITY = "audit-event";
    private static final Sort NEWEST_FIRST = Sort.by(Sort.Order.desc("occurredAt"), Sort.Order.desc("id"));

    private final AuditEventRepository events;
    private final RowScopes scopes;
    private final FieldRules fields;
    private final TransactionTemplate transactions;

    /**
     * Creates the search.
     *
     * @param events the event store
     * @param scopes the events each reader may see or export
     * @param fields how each reader sees the secured fields
     * @param transactionManager opens transactions
     */
    public AuditSearch(AuditEventRepository events, RowScopes scopes, FieldRules fields, PlatformTransactionManager transactionManager)
    {
        this.events = requireNonNull(events, "events");
        this.scopes = requireNonNull(scopes, "scopes");
        this.fields = requireNonNull(fields, "fields");
        this.transactions = new TransactionTemplate(requireNonNull(transactionManager, "transactionManager"));
        transactions.setReadOnly(true);
    }

    /**
     * Returns a page of the events a reader may see.
     *
     * @param readerId the reader
     * @param query the filters
     * @param limit how many at most; at most {@value #MAX_PAGE}
     * @param cursor where to go on from, as an earlier page's next cursor, or {@code null} to start
     * @return the events and the cursor of the next page
     * @throws GrantForgeException with {@link CommonErrorCode#BAD_REQUEST} for a malformed cursor
     */
    public CursorPage<AuditEventView> search(long readerId, AuditQuery query, int limit, @Nullable String cursor)
    {
        int size = Math.max(1, Math.min(limit, MAX_PAGE));
        Position after = cursor == null || cursor.isBlank() ? null : Position.decode(cursor);
        return requireNonNull(transactions.execute(status -> {
            Specification<AuditEvent> where = scopes.scope(readerId, AuditEvent.class, DataAction.READ).and(matching(query, after));
            List<AuditEvent> found = events.findBy(where, fluent -> fluent.sortBy(NEWEST_FIRST).limit(size + 1).all());
            List<AuditEvent> page = found.subList(0, Math.min(size, found.size()));
            AuditEvent last = page.isEmpty() ? null : page.get(page.size() - 1);
            return new CursorPage<>(page.stream().map(AuditEventView::from).toList(),
                    found.size() > size && last != null ? new Position(last.getOccurredAt(), last.requireId()).encode() : null);
        }));
    }

    /**
     * Exports the events a reader may export, newest first, at most {@value #MAX_EXPORT}; secured fields are hidden or masked
     * as the reader sees them, a hidden field's column staying empty.
     *
     * @param readerId the reader
     * @param query the filters
     * @return the header ({@link #COLUMNS}) and one row per event
     */
    public List<List<String>> export(long readerId, AuditQuery query)
    {
        FieldView clientIp = fields.read(readerId, ENTITY, "clientIp");
        FieldView userAgent = fields.read(readerId, ENTITY, "userAgent");
        return requireNonNull(transactions.execute(status -> {
            Specification<AuditEvent> where = scopes.scope(readerId, AuditEvent.class, DataAction.EXPORT).and(matching(query, null));
            List<List<String>> rows = new ArrayList<>();
            rows.add(COLUMNS);
            events.findBy(where, fluent -> fluent.sortBy(NEWEST_FIRST).limit(MAX_EXPORT).all()).forEach(event -> rows.add(List.of(
                    event.getOccurredAt().toString(), event.getAction().name(), event.getOutcome().name(), text(event.getTenantId()),
                    text(event.getActorId()), text(event.getActorName()), text(event.getTargetId()), text(event.getReason()),
                    text(clientIp.present(event.getClientIp())), text(userAgent.present(event.getUserAgent())), text(event.getRequestId()))));
            return rows;
        }));
    }

    private static Specification<AuditEvent> matching(AuditQuery query, @Nullable Position after)
    {
        return (root, criteria, builder) -> {
            List<Predicate> where = new ArrayList<>();
            if (query.action() != null) {
                where.add(builder.equal(root.get("action"), query.action()));
            }
            if (query.outcome() != null) {
                where.add(builder.equal(root.get("outcome"), query.outcome()));
            }
            String actor = blankToNull(query.actor());
            if (actor != null) {
                where.add(builder.like(builder.lower(root.get("actorName")), contains(actor)));
            }
            String target = blankToNull(query.target());
            if (target != null) {
                where.add(builder.equal(root.get("targetId"), target));
            }
            if (query.from() != null) {
                where.add(builder.greaterThanOrEqualTo(root.get("occurredAt"), query.from()));
            }
            if (query.until() != null) {
                where.add(builder.lessThan(root.get("occurredAt"), query.until()));
            }
            if (after != null) {
                where.add(builder.or(builder.lessThan(root.get("occurredAt"), after.occurredAt()),
                        builder.and(builder.equal(root.get("occurredAt"), after.occurredAt()), builder.lessThan(root.get("id"), after.id()))));
            }
            return builder.and(where.toArray(Predicate[]::new));
        };
    }

    private static @Nullable String blankToNull(@Nullable String value)
    {
        return value == null || value.isBlank() ? null : value.strip();
    }

    /** A LIKE pattern for text anywhere; wildcards typed by the user are taken literally by dropping them. */
    private static String contains(String text)
    {
        return "%" + text.toLowerCase(Locale.ROOT).replace("\\", "").replace("%", "").replace("_", "") + "%";
    }

    private static String text(@Nullable Object value)
    {
        return value == null ? "" : value.toString();
    }

    /** Where a page ended: the time and ID of its last event. */
    record Position(Instant occurredAt, long id)
    {
        String encode()
        {
            return Base64.getUrlEncoder().withoutPadding().encodeToString((occurredAt + "~" + id).getBytes(StandardCharsets.UTF_8));
        }

        static Position decode(String cursor)
        {
            try {
                String text = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
                int split = text.indexOf('~');
                return new Position(Instant.parse(text.substring(0, split)), Long.parseLong(text.substring(split + 1)));
            }
            catch (IllegalArgumentException | DateTimeParseException | IndexOutOfBoundsException malformed) {
                throw new GrantForgeException(CommonErrorCode.BAD_REQUEST, "malformed cursor", malformed);
            }
        }
    }
}

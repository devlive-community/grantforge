// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.audit.application;

import org.devlive.grantforge.audit.domain.AuditAction;
import org.devlive.grantforge.audit.domain.AuditEvent;
import org.devlive.grantforge.audit.domain.AuditEventRepository;
import org.devlive.grantforge.audit.domain.AuditOutcome;
import org.devlive.grantforge.common.error.CommonErrorCode;
import org.devlive.grantforge.common.error.GrantForgeException;
import org.devlive.grantforge.common.page.CursorPage;
import org.devlive.grantforge.persistence.secured.DataAction;
import org.devlive.grantforge.persistence.secured.FieldRules;
import org.devlive.grantforge.persistence.secured.FieldView;
import org.devlive.grantforge.persistence.secured.MaskStrategy;
import org.devlive.grantforge.persistence.secured.RowScopes;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class AuditSearchTest
{
    private static final Instant T0 = Instant.parse("2026-10-01T08:00:00Z");

    @Autowired
    private AuditEventRepository events;

    @Autowired
    private PlatformTransactionManager transactionManager;

    /** Readers see only the events of tenant 1; exports also leave out failures. */
    private final RowScopes scopes = new RowScopes()
    {
        @Override
        @SuppressWarnings("unchecked")
        public <T> Specification<T> scope(long accountId, Class<T> type, DataAction action)
        {
            Specification<T> tenant = (root, query, builder) -> builder.equal(root.get("tenantId"), 1L);
            return action == DataAction.EXPORT ? tenant.and((root, query, builder) -> builder.equal(root.get("outcome"), AuditOutcome.SUCCESS))
                    : tenant;
        }
    };
    private final FieldRules fields = (accountId, entity, field) -> field.equals("clientIp") ? FieldView.masked(MaskStrategy.PARTIAL)
            : field.equals("userAgent") ? FieldView.HIDDEN : FieldView.VISIBLE;

    private AuditSearch search;

    @BeforeEach
    void createEvents()
    {
        search = new AuditSearch(events, scopes, fields, transactionManager);
        List<AuditEvent> made = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            made.add(event(T0.plusSeconds(i), i % 2 == 0 ? AuditOutcome.SUCCESS : AuditOutcome.FAILURE, 1L, "alice", "target-" + i));
        }
        made.add(event(T0.plusSeconds(1), AuditOutcome.SUCCESS, 2L, "eve", "elsewhere"));
        events.saveAll(made);
    }

    @AfterEach
    void deleteRows()
    {
        events.deleteAllInBatch();
    }

    private static AuditEvent event(Instant at, AuditOutcome outcome, long tenant, String actor, String target)
    {
        return AuditEvent.of(at, AuditAction.LOGIN_SUCCEEDED, outcome, tenant, 7L, actor, target, null, "10.0.0.1", "Firefox", "req");
    }

    private List<String> targets(AuditQuery query)
    {
        return search.search(9, query, 50, null).items().stream().map(AuditEventView::targetId).toList();
    }

    @Test
    void pagesThroughTheEventsTheReaderMaySeeNewestFirst()
    {
        CursorPage<AuditEventView> first = search.search(9, AuditQuery.ALL, 2, null);
        assertThat(first.items()).extracting(AuditEventView::targetId).containsExactly("target-4", "target-3");
        CursorPage<AuditEventView> second = search.search(9, AuditQuery.ALL, 2, first.nextCursor());
        assertThat(second.items()).extracting(AuditEventView::targetId).containsExactly("target-2", "target-1");
        CursorPage<AuditEventView> last = search.search(9, AuditQuery.ALL, 2, second.nextCursor());
        assertThat(last.items()).extracting(AuditEventView::targetId).containsExactly("target-0");
        assertThat(last.hasNext()).isFalse();
        assertThat(search.search(9, AuditQuery.ALL, 0, null).items()).hasSize(1);
        assertThatThrownBy(() -> search.search(9, AuditQuery.ALL, 2, "not a cursor")).isInstanceOfSatisfying(GrantForgeException.class,
                error -> assertThat(error.getErrorCode()).isEqualTo(CommonErrorCode.BAD_REQUEST));
    }

    @Test
    void filtersByActionOutcomeActorTargetAndTime()
    {
        assertThat(targets(new AuditQuery(null, AuditOutcome.FAILURE, null, null, null, null))).containsExactly("target-3", "target-1");
        assertThat(targets(new AuditQuery(AuditAction.LOGOUT, null, null, null, null, null))).isEmpty();
        assertThat(targets(new AuditQuery(null, null, " ALI ", null, null, null))).hasSize(5);
        assertThat(targets(new AuditQuery(null, null, "eve", null, null, null))).isEmpty();
        assertThat(targets(new AuditQuery(null, null, null, "target-2", null, null))).containsExactly("target-2");
        assertThat(targets(new AuditQuery(AuditAction.LOGIN_SUCCEEDED, null, null, null, T0.plusSeconds(1), T0.plusSeconds(3))))
                .containsExactly("target-2", "target-1");
    }

    @Test
    void exportsWhatTheReaderMayExportAsTheReaderSeesIt()
    {
        List<List<String>> rows = search.export(9, AuditQuery.ALL);
        assertThat(rows.get(0)).isEqualTo(AuditSearch.COLUMNS);
        assertThat(rows).hasSize(4);
        assertThat(rows.get(1)).containsExactly(T0.plusSeconds(4).toString(), "LOGIN_SUCCEEDED", "SUCCESS", "1", "7", "alice", "target-4",
                "", "1***1", "", "req");
    }
}

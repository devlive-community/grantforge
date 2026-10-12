// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.mysql.agent.sql;

import org.devlive.grantforge.agent.AccessEvent;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class VerdictTest
{
    @Test
    void auditEventsCannotBeChangedAfterTheStatementHasBeenDecided()
    {
        AccessEvent allowed = AccessEvent.builder("alice", "sales.orders.id", "select", true).build();
        AccessEvent denied = AccessEvent.builder("alice", "sales.orders.card", "select", false).build();
        List<AccessEvent> events = new ArrayList<>(List.of(allowed, denied));
        Refusal refusal = new Refusal(Refusal.COLUMN_DENIED, "card denied");
        Verdict verdict = new Verdict(null, refusal, events);
        events.clear();

        assertThat(verdict.events()).containsExactly(allowed, denied);
        assertThatThrownBy(() -> verdict.events().clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThat(verdict.allowed()).isFalse();
        assertThat(verdict.refusal()).isSameAs(refusal);
    }

    @Test
    void distinguishesUnchangedAndRewrittenAllowedQueriesFromRefusals()
    {
        Verdict unchanged = new Verdict(null, null, List.of());
        Verdict rewritten = new Verdict("SELECT NULL AS card FROM orders", null, List.of());
        Verdict refused = new Verdict(null, new Refusal(Refusal.TABLE_DENIED, "orders denied"), List.of());

        assertThat(unchanged.allowed()).isTrue();
        assertThat(unchanged.sql()).isNull();
        assertThat(rewritten.allowed()).isTrue();
        assertThat(rewritten.sql()).isEqualTo("SELECT NULL AS card FROM orders");
        assertThat(refused.allowed()).isFalse();
        assertThat(refused.sql()).isNull();
    }

    @Test
    @SuppressWarnings("NullAway") // deliberately violates the non-null contract to test the guards
    void refusesMissingAuditCollectionsOrNullEvents()
    {
        assertThatNullPointerException().isThrownBy(() -> new Verdict(null, null, null));
        List<AccessEvent> events = new ArrayList<>();
        events.add(null);
        assertThatNullPointerException().isThrownBy(() -> new Verdict(null, null, events));
    }
}

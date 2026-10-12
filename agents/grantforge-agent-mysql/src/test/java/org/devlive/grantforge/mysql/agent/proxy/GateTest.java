// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.mysql.agent.proxy;

import org.devlive.grantforge.agent.AccessEvent;
import org.devlive.grantforge.mysql.agent.sql.Fixtures;
import org.devlive.grantforge.mysql.agent.sql.Policies;
import org.devlive.grantforge.mysql.agent.sql.Refusal;
import org.devlive.grantforge.mysql.agent.sql.Verdict;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class GateTest
{
    @Test
    void checksStatementsAgainstTheCatalogAndTheCurrentSnapshot() throws Refusal
    {
        AtomicReference<Policies> policies = new AtomicReference<>(Policies.of(null));
        List<AccessEvent> events = new ArrayList<>();
        Gate gate = new Gate(Fixtures.catalog(), policies::get, events::add, Runnable::run);
        String sql = "SELECT card FROM orders";
        Verdict denied = gate.guard().check(sql, gate.analyzer().analyze(sql, "sales"), "alice", "127.0.0.1", gate.policies().get(), false);
        assertThat(denied.allowed()).isFalse();
        policies.set(new Fixtures.Policy().allow("sales", "orders", "card", List.of("alice"), "select").policies());
        Verdict allowed = gate.guard().check(sql, gate.analyzer().analyze(sql, "sales"), "alice", "127.0.0.1", gate.policies().get(), false);
        assertThat(allowed.allowed()).isTrue();
        gate.executor().execute(() -> allowed.events().forEach(gate.events()));
        assertThat(events).containsExactlyElementsOf(allowed.events());
    }

    @Test
    @SuppressWarnings("NullAway") // Invalid input is the behavior under test.
    void rejectsMissingCollaboratorsBeforeAcceptingConnections()
    {
        assertThatNullPointerException().isThrownBy(() -> new Gate(null, () -> Policies.of(null), event -> { }, Runnable::run))
                .withMessage("catalog");
        assertThatNullPointerException().isThrownBy(() -> new Gate(Fixtures.catalog(), null, event -> { }, Runnable::run)).withMessage("policies");
        assertThatNullPointerException().isThrownBy(() -> new Gate(Fixtures.catalog(), () -> Policies.of(null), null, Runnable::run))
                .withMessage("events");
        assertThatNullPointerException().isThrownBy(() -> new Gate(Fixtures.catalog(), () -> Policies.of(null), event -> { }, null))
                .withMessage("executor");
    }
}

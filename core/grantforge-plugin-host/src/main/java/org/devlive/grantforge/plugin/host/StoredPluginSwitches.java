// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.plugin.host;

import org.devlive.grantforge.plugin.host.domain.PluginState;
import org.devlive.grantforge.plugin.host.domain.PluginStateRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;

import static java.util.Objects.requireNonNull;

/** Plugin switches kept in {@code gf_plugin_state}, shared by every node. */
@Component
public final class StoredPluginSwitches
        implements PluginSwitches
{
    private final PluginStateRepository states;
    private final TransactionTemplate transactions;
    private final Clock clock;

    /**
     * Creates the switches.
     *
     * @param states the stored switches
     * @param transactionManager opens transactions
     * @param clock stamps changes
     */
    public StoredPluginSwitches(PluginStateRepository states, PlatformTransactionManager transactionManager, Clock clock)
    {
        this.states = requireNonNull(states, "states");
        this.transactions = new TransactionTemplate(requireNonNull(transactionManager, "transactionManager"));
        this.clock = requireNonNull(clock, "clock");
    }

    @Override
    public boolean enabled(String pluginId)
    {
        return Boolean.TRUE.equals(transactions.execute(status -> states.findById(pluginId).map(PluginState::isEnabled)
                .orElse(true)));
    }

    @Override
    public void set(String pluginId, boolean enabled)
    {
        transactions.executeWithoutResult(status -> states.findById(pluginId).ifPresentOrElse(
                state -> state.set(enabled, clock.instant()),
                () -> states.save(PluginState.of(pluginId, enabled, clock.instant()))));
    }
}

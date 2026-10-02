// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.plugin.host.domain;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class PluginStateRepositoryTest
{
    @Autowired
    private PluginStateRepository states;

    @AfterEach
    void deleteRows()
    {
        states.deleteAll();
    }

    @Test
    void storesOneSwitchPerPlugin()
    {
        states.save(PluginState.of("hdfs", false, Instant.EPOCH));
        states.save(PluginState.of("hdfs", true, Instant.EPOCH));
        assertThat(states.findAll()).singleElement().satisfies(state -> assertThat(state.isEnabled()).isTrue());
    }
}

// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.plugin.host;

import org.devlive.grantforge.plugin.host.domain.PluginStateRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import(StoredPluginSwitches.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class StoredPluginSwitchesTest
{
    @Autowired
    private StoredPluginSwitches switches;

    @Autowired
    private PluginStateRepository states;

    @AfterEach
    void deleteRows()
    {
        states.deleteAll();
    }

    @Test
    void pluginsAreOnUntilSwitchedOffAndCanBeSwitchedBack()
    {
        assertThat(switches.enabled("hdfs")).isTrue();
        switches.set("hdfs", false);
        assertThat(switches.enabled("hdfs")).isFalse();
        switches.set("hdfs", true);
        assertThat(switches.enabled("hdfs")).isTrue();
        assertThat(states.findAll()).singleElement().satisfies(state -> {
            assertThat(state.getPluginId()).isEqualTo("hdfs");
            assertThat(state.isEnabled()).isTrue();
        });
    }
}

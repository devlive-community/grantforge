// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.agent.sync;

import org.junit.jupiter.api.Test;

import java.util.logging.Level;
import java.util.logging.Logger;

import static org.assertj.core.api.Assertions.assertThat;

class NoticesTest
{
    @Test
    void saysEachThingOnceAtItsLevel()
    {
        Logger logger = Logger.getLogger(NoticesTest.class.getName() + ".once");
        try (Logs logs = Logs.of(logger)) {
            Notices notices = new Notices(logger);
            assertThat(notices.once(Level.WARNING, "user etl is left alone")).isTrue();
            assertThat(notices.once(Level.WARNING, "user etl is left alone")).isFalse();
            assertThat(notices.once(Level.INFO, "user backup is left alone")).isTrue();
            assertThat(logs.lines()).containsExactly("WARNING user etl is left alone", "INFO user backup is left alone");
        }
    }
}

// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.plugin.api;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ServiceConfigTest
{
    private static final ServiceConfig CONFIG = new ServiceConfig("files",
            Map.of("host", "nn", "port", "+8020", "tls", "true", "password", "s3cret"));

    @Test
    void configurationsReadTypedValuesAndHideThemWhenPrinted()
    {
        assertThat(CONFIG.get("host")).isEqualTo("nn");
        assertThat(CONFIG.get("missing")).isNull();
        assertThat(CONFIG.require("host")).isEqualTo("nn");
        assertThatThrownBy(() -> CONFIG.require("missing")).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("files has no value for missing");
        assertThat(CONFIG.getLong("port", 1)).isEqualTo(8020);
        assertThat(CONFIG.getLong("missing", 7)).isEqualTo(7);
        assertThat(CONFIG.getBoolean("tls", false)).isTrue();
        assertThat(CONFIG.getBoolean("host", true)).isFalse();
        assertThat(CONFIG.getBoolean("missing", true)).isTrue();
        assertThat(CONFIG).hasToString("ServiceConfig[files, fields=[host, password, port, tls]]");
        assertThat(CONFIG.toString()).doesNotContain("s3cret");
    }
}

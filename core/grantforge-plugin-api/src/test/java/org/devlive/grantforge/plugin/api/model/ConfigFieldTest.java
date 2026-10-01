// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.plugin.api.model;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConfigFieldTest
{
    @Test
    void configFieldsCheckValuesByType()
    {
        ConfigField port = ConfigField.builder("port").type(ConfigFieldType.INTEGER).build();
        ConfigField mode = ConfigField.builder("mode").label("Mode").options("a", "b").description(" help ").build();
        ConfigField secret = ConfigField.builder("password").type(ConfigFieldType.SECRET).mandatory().build();
        ConfigField text = ConfigField.builder("note").type(ConfigFieldType.TEXT).build();

        assertThat(port.check("+8020")).isEmpty();
        assertThat(port.check("99999999999999999999")).contains(ConfigProblem.Reason.NOT_AN_INTEGER);
        assertThat(mode.type()).isEqualTo(ConfigFieldType.ENUM);
        assertThat(mode.description()).isEqualTo("help");
        assertThat(mode.check("c")).contains(ConfigProblem.Reason.NOT_AN_OPTION);
        assertThat(secret.check("anything")).isEmpty();
        assertThat(secret.sensitive()).isTrue();
        assertThat(port.sensitive()).isFalse();
        assertThat(text.check("line 1\nline 2")).isEmpty();
        assertThat(ConfigField.builder("flag").type(ConfigFieldType.BOOLEAN).build().check("true")).isEmpty();
    }

    @Test
    void configFieldsRefuseMistakes()
    {
        assertThatThrownBy(() -> ConfigField.builder("1st").build()).hasMessageContaining("malformed config field name");
        assertThatThrownBy(() -> ConfigField.builder("mode").type(ConfigFieldType.ENUM).build())
                .hasMessageContaining("options are required");
        assertThatThrownBy(() -> new ConfigField("mode", "Mode", ConfigFieldType.STRING, false, null, List.of("a"), null,
                null)).hasMessageContaining("options are required");
        assertThatThrownBy(() -> ConfigField.builder("mode").options("a", "a").build()).hasMessageContaining("repeats");
        assertThatThrownBy(() -> ConfigField.builder("port").type(ConfigFieldType.INTEGER).pattern("\\d+").build())
                .hasMessageContaining("STRING or TEXT");
        assertThatThrownBy(() -> ConfigField.builder("host").pattern("(").build()).hasMessageContaining("invalid pattern");
        assertThatThrownBy(() -> ConfigField.builder("key").type(ConfigFieldType.SECRET).defaultValue("x").build())
                .hasMessageContaining("cannot have a default");
        assertThatThrownBy(() -> ConfigField.builder("port").type(ConfigFieldType.INTEGER).defaultValue("x").build())
                .hasMessageContaining("invalid default");
        assertThat(ConfigField.builder("host").pattern(" ").build().pattern()).isNull();
    }
}

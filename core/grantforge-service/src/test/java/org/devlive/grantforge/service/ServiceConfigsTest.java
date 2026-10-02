// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.service;

import org.devlive.grantforge.common.error.FieldIssue;
import org.devlive.grantforge.plugin.api.model.ConfigProblem;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ServiceConfigsTest
{
    @Test
    void appliesDefaultsKeepsStoredSecretsAndNamesEveryProblem()
    {
        ServiceConfigs.Resolved resolved = ServiceConfigs.resolve(new DemoProvider().definition(),
                Map.of("url", "demo://x", "password", " "), Map.of("password", "sealed"), value -> "sealed:" + value,
                value -> "opened:" + value);
        assertThat(resolved.plain()).containsOnly(Map.entry("url", "demo://x"));
        assertThat(resolved.secrets()).containsOnly(Map.entry("password", "sealed"));
        assertThat(resolved.effective()).containsOnly(Map.entry("url", "demo://x"), Map.entry("timeout", "30"),
                Map.entry("password", "opened:sealed"));
        assertThat(resolved.issues()).isEmpty();

        assertThat(ServiceConfigs.issues(List.of(new ConfigProblem("ssl", ConfigProblem.Reason.INVALID, null),
                ConfigProblem.of("url", ConfigProblem.Reason.PATTERN_MISMATCH)))).containsExactly(
                FieldIssue.of("ssl", "error.service.config.invalid", ""),
                FieldIssue.of("url", "error.service.config.pattern-mismatch"));
        assertThat(ServiceConfigs.messageKey(ConfigProblem.Reason.UNKNOWN_FIELD)).isEqualTo("error.service.config.unknown-field");
    }
}

// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.yarn;

import org.apache.hadoop.conf.Configuration;
import org.devlive.grantforge.plugin.api.ServiceConfig;
import org.devlive.grantforge.plugin.api.model.ConfigProblem;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class YarnSettingsTest
{
    /** A value that removes the field. */
    static final String UNSET = "<unset>";

    static ServiceConfig config(String... values)
    {
        Map<String, String> map = new HashMap<>(Map.of(YarnSettings.RESOURCE_MANAGER, "rm1.example.com", YarnSettings.USER, "grantforge"));
        for (int index = 0; index < values.length; index += 2) {
            if (UNSET.equals(values[index + 1])) {
                map.remove(values[index]);
            }
            else {
                map.put(values[index], values[index + 1]);
            }
        }
        return new ServiceConfig("compute", map);
    }

    private static List<String> fields(ServiceConfig config)
    {
        return YarnSettings.problems(config).stream().map(ConfigProblem::field).toList();
    }

    @Test
    void readsASimpleSignInWithTheDefaultPort()
    {
        assertThat(YarnSettings.problems(config())).isEmpty();
        YarnSettings settings = YarnSettings.of(config());
        assertThat(settings.resourceManagers()).containsExactly("rm1.example.com:8032");
        assertThat(settings.kerberos()).isFalse();
        assertThat(settings.timeout()).isEqualTo(YarnSettings.DEFAULT_TIMEOUT);
        Configuration client = settings.client();
        assertThat(client.get(YarnSettings.RESOURCE_MANAGER)).isEqualTo("rm1.example.com:8032");
        assertThat(client.getBoolean("yarn.resourcemanager.ha.enabled", false)).isFalse();
        assertThat(client.get(YarnSettings.AUTHENTICATION)).isEqualTo("simple");
        assertThat(client.getLong("yarn.resourcemanager.connect.max-wait.ms", 0)).isEqualTo(30_000);
        assertThat(client.getInt("ipc.client.connect.timeout", 0)).isEqualTo(20_000);
        assertThat(client.get(YarnSettings.PRINCIPAL)).isNull();
    }

    @Test
    void failsOverBetweenSeveralResourceManagers()
    {
        YarnSettings settings = YarnSettings.of(config(YarnSettings.RESOURCE_MANAGER, "rm1:8032, rm2:9032,rm1:8032", YarnSettings.TIMEOUT, "500"));
        assertThat(settings.resourceManagers()).containsExactly("rm1:8032", "rm2:9032");
        Configuration client = settings.client();
        assertThat(client.getBoolean("yarn.resourcemanager.ha.enabled", false)).isTrue();
        assertThat(client.get("yarn.resourcemanager.ha.rm-ids")).isEqualTo("rm1,rm2");
        assertThat(client.get("yarn.resourcemanager.address.rm2")).isEqualTo("rm2:9032");
        assertThat(client.get("yarn.resourcemanager.hostname.rm2")).isEqualTo("rm2");
        assertThat(client.getInt("yarn.client.failover-max-attempts", 0)).isEqualTo(4);
        assertThat(client.getLong("yarn.resourcemanager.connect.retry-interval.ms", 0)).isEqualTo(500);
        assertThat(YarnSettings.of(config(YarnSettings.RESOURCE_MANAGER, "[::1]:8032")).resourceManagers()).containsExactly("[::1]:8032");
    }

    @ParameterizedTest
    @ValueSource(strings = {"rm1:0", "rm1:70000", "rm 1", "rm1,", "http://rm1:8032"})
    void refusesWrongAddresses(String address)
    {
        assertThat(fields(config(YarnSettings.RESOURCE_MANAGER, address))).containsExactly(YarnSettings.RESOURCE_MANAGER);
    }

    @Test
    void requiresTheResourceManagerAndTheUser()
    {
        assertThat(YarnSettings.problems(config(YarnSettings.RESOURCE_MANAGER, UNSET, YarnSettings.USER, " ")))
                .extracting(ConfigProblem::field, ConfigProblem::reason).containsExactly(
                        org.assertj.core.groups.Tuple.tuple(YarnSettings.RESOURCE_MANAGER, ConfigProblem.Reason.REQUIRED),
                        org.assertj.core.groups.Tuple.tuple(YarnSettings.USER, ConfigProblem.Reason.REQUIRED));
        assertThatIllegalArgumentException().isThrownBy(() -> YarnSettings.of(config(YarnSettings.USER, UNSET)))
                .withMessageStartingWith("username: ");
    }

    @Test
    void readsAKerberosSignIn()
    {
        YarnSettings settings = YarnSettings.of(config(YarnSettings.AUTHENTICATION, "Kerberos", YarnSettings.USER, "grantforge@EXAMPLE.COM",
                YarnSettings.KEYTAB, "/etc/grantforge/yarn.keytab", YarnSettings.KDC, "kdc.example.com:88", YarnSettings.PROTECTION, "privacy"));
        assertThat(settings.kerberos()).isTrue();
        assertThat(settings.principal()).isEqualTo("yarn/_HOST@EXAMPLE.COM");
        Configuration client = settings.client();
        assertThat(client.get(YarnSettings.AUTHENTICATION)).isEqualTo("kerberos");
        assertThat(client.get(YarnSettings.PRINCIPAL)).isEqualTo("yarn/_HOST@EXAMPLE.COM");
        assertThat(client.get(YarnSettings.PROTECTION)).isEqualTo("privacy");
        assertThat(YarnSettings.of(config(YarnSettings.AUTHENTICATION, "kerberos", YarnSettings.USER, "grantforge@EXAMPLE.COM",
                YarnSettings.PASSWORD, "secret", YarnSettings.PRINCIPAL, "rm/_HOST@CORP.COM")).principal()).isEqualTo("rm/_HOST@CORP.COM");
    }

    @Test
    void checksAKerberosSignIn()
    {
        assertThat(fields(config(YarnSettings.AUTHENTICATION, "kerberos"))).containsExactly(YarnSettings.USER, YarnSettings.PASSWORD);
        assertThat(fields(config(YarnSettings.AUTHENTICATION, "kerberos", YarnSettings.USER, "a@EXAMPLE.COM", YarnSettings.PASSWORD, "x",
                YarnSettings.KDC, "kdc:port", YarnSettings.PRINCIPAL, "yarn"))).containsExactly(YarnSettings.KDC, YarnSettings.PRINCIPAL);
        assertThat(fields(config(YarnSettings.PASSWORD, "x", YarnSettings.KEYTAB, "/k", YarnSettings.KDC, "kdc", YarnSettings.PRINCIPAL,
                "yarn/_HOST@EXAMPLE.COM"))).as("only Kerberos uses them").containsExactly(YarnSettings.PASSWORD, YarnSettings.KEYTAB,
                YarnSettings.KDC, YarnSettings.PRINCIPAL);
    }

    @Test
    void checksTheAuthenticationProtectionAndTimeout()
    {
        assertThat(fields(config(YarnSettings.AUTHENTICATION, "token"))).containsExactly(YarnSettings.AUTHENTICATION);
        assertThat(fields(config(YarnSettings.PROTECTION, "secret"))).containsExactly(YarnSettings.PROTECTION);
        for (String timeout : List.of("0", "600001", "soon", "1234567")) {
            assertThat(fields(config(YarnSettings.TIMEOUT, timeout))).as(timeout).containsExactly(YarnSettings.TIMEOUT);
        }
        assertThat(YarnSettings.timeout(config(YarnSettings.TIMEOUT, "600000"))).isEqualTo(600_000);
        assertThat(YarnSettings.timeout(config(YarnSettings.TIMEOUT, " "))).isEqualTo(YarnSettings.DEFAULT_TIMEOUT);
    }
}

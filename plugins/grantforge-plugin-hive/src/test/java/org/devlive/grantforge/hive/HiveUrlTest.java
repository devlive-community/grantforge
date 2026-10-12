// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.hive;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Locale;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class HiveUrlTest
{
    @Test
    void readsTheSessionVariablesOfEveryForm()
    {
        assertThat(HiveUrl.check("jdbc:hive2://hs2:10000/default", false)).isEmpty();
        assertThat(HiveUrl.check(" jdbc:hive2://hs2:10000 ", false)).isEmpty();
        assertThat(HiveUrl.check("jdbc:hive2://zk1:2181,zk2:2181/;serviceDiscoveryMode=zooKeeper;zooKeeperNamespace=hiveserver2", false))
                .containsExactly(Map.entry("serviceDiscoveryMode", "zooKeeper"), Map.entry("zooKeeperNamespace", "hiveserver2"));
        assertThat(HiveUrl.check("jdbc:hive2://hs2:10001/sales;ssl=true;sslTrustStore=/etc/ts.jks;transportMode=http;httpPath=cliservice"
                + "?hive.exec.parallel=true#var=1", false)).containsKeys("ssl", "sslTrustStore", "transportMode", "httpPath")
                .doesNotContainKey("hive.exec.parallel");
        assertThat(HiveUrl.check("jdbc:hive2://hs2:10000/default;auth=noSasl;;", false)).containsExactly(Map.entry("auth", "noSasl"));
        assertThat(HiveUrl.check("jdbc:hive2://hs2:10000/default;principal=hive/_HOST@EXAMPLE.COM", true))
                .containsExactly(Map.entry("principal", "hive/_HOST@EXAMPLE.COM"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"jdbc:hive://hs2:10000", "jdbc:mysql://db", "jdbc:hive2://", "jdbc:hive2:///default", "jdbc:hive2://bob@hs2:10000/default"})
    void refusesUrlsThatNameNoHiveServer(String url)
    {
        assertThatIllegalArgumentException().isThrownBy(() -> HiveUrl.check(url, false));
    }

    @ParameterizedTest
    @ValueSource(strings = {"user", "password", "initFile", "kerberosAuthType", "delegationToken", "storePasswordPath", "jwt",
            "browserResponsePort", "browserAnything"})
    void refusesVariablesThatCarryCredentialsOrReadFilesWhateverTheirCase(String key)
    {
        for (String spelled : new String[] {key, key.toUpperCase(Locale.ROOT)}) {
            assertThatIllegalArgumentException().isThrownBy(() -> HiveUrl.check("jdbc:hive2://hs2:10000/default;" + spelled + "=x", false))
                    .withMessageContaining("cannot be set for a service");
        }
    }

    @Test
    void keepsAuthenticationToTheServicesOwnFields()
    {
        assertThatIllegalArgumentException().isThrownBy(() -> HiveUrl.check("jdbc:hive2://hs2:10000/;auth=browser", false))
                .withMessageContaining("noSasl");
        assertThatIllegalArgumentException().isThrownBy(() -> HiveUrl.check("jdbc:hive2://hs2:10000/;auth=noSasl;principal=hive/h@R", true))
                .withMessageContaining("without auth");
        assertThatIllegalArgumentException().isThrownBy(() -> HiveUrl.check("jdbc:hive2://hs2:10000/default", true))
                .withMessageContaining("principal=hive/_HOST@EXAMPLE.COM");
        assertThatIllegalArgumentException().isThrownBy(() -> HiveUrl.check("jdbc:hive2://hs2:10000/;principal=hive/h@R", false))
                .withMessageContaining("choose kerberos");
    }

    @Test
    void refusesMalformedAndRepeatedVariables()
    {
        assertThatIllegalArgumentException().isThrownBy(() -> HiveUrl.check("jdbc:hive2://hs2:10000/;=x", false))
                .withMessageContaining("no variable name");
        assertThatIllegalArgumentException().isThrownBy(() -> HiveUrl.check("jdbc:hive2://hs2:10000/;ssl=true;SSL=false", false))
                .withMessageContaining("given twice");
    }

    @Test
    void addsTheSignInAndTheTimeoutBeforeTheHiveSettings()
    {
        assertThat(HiveUrl.connect("jdbc:hive2://hs2:10000/default", false, 5000)).isEqualTo("jdbc:hive2://hs2:10000/default;socketTimeout=5000");
        assertThat(HiveUrl.connect("jdbc:hive2://hs2:10000/default;principal=hive/_HOST@R?a=b#c=d", true, 5000))
                .isEqualTo("jdbc:hive2://hs2:10000/default;principal=hive/_HOST@R;kerberosAuthType=fromSubject;socketTimeout=5000?a=b#c=d");
        assertThat(HiveUrl.connect("jdbc:hive2://hs2:10000/;socketTimeout=900#v=1", false, 5000)).isEqualTo("jdbc:hive2://hs2:10000/;socketTimeout=900#v=1");
    }
}

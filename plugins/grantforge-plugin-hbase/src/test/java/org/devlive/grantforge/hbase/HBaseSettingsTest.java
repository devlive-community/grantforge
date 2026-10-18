// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.hbase;

import org.apache.hadoop.conf.Configuration;
import org.devlive.grantforge.plugin.api.ServiceConfig;
import org.devlive.grantforge.plugin.api.model.ConfigProblem;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HBaseSettingsTest
{
    static ServiceConfig config(String... values)
    {
        Map<String, String> map = new HashMap<>();
        map.put(HBaseSettings.QUORUM, "zk1, zk2:2182");
        map.put(HBaseSettings.USER, "grantforge");
        for (int index = 0; index < values.length; index += 2) {
            if (values[index + 1].isEmpty()) {
                map.remove(values[index]);
            }
            else {
                map.put(values[index], values[index + 1]);
            }
        }
        return new ServiceConfig("tables", map);
    }

    private static List<String> fields(ServiceConfig config)
    {
        return HBaseSettings.problems(config).stream().map(ConfigProblem::field).toList();
    }

    private static ServiceConfig kerberos(String... values)
    {
        String[] all = new String[values.length + 6];
        all[0] = HBaseSettings.AUTHENTICATION;
        all[1] = "Kerberos";
        all[2] = HBaseSettings.USER;
        all[3] = "grantforge@EXAMPLE.COM";
        all[4] = HBaseSettings.PASSWORD;
        all[5] = "secret";
        System.arraycopy(values, 0, all, 6, values.length);
        return config(all);
    }

    @Test
    void aQuorumAndAUserAreEnough()
    {
        assertThat(HBaseSettings.problems(config())).isEmpty();
        HBaseSettings settings = HBaseSettings.of(config());
        assertThat(settings.quorum()).isEqualTo("zk1,zk2:2182");
        assertThat(settings.zookeeperPort()).isEqualTo(2181);
        assertThat(settings.znode()).isEqualTo("/hbase");
        assertThat(settings.bootstrap()).isNull();
        assertThat(settings.kerberos()).isFalse();
        assertThat(settings.user()).isEqualTo("grantforge");
        assertThat(settings.password()).isNull();
        assertThat(settings.masterPrincipal()).isEmpty();
        assertThat(settings.protection()).isEqualTo("authentication");
        assertThat(settings.timeout()).isEqualTo(30_000);
        Configuration client = settings.client();
        assertThat(client.get(HBaseSettings.QUORUM)).isEqualTo("zk1,zk2:2182");
        assertThat(client.getInt(HBaseSettings.ZOOKEEPER_PORT, 0)).isEqualTo(2181);
        assertThat(client.get(HBaseSettings.ZNODE)).isEqualTo("/hbase");
        assertThat(client.get("hbase.client.registry.impl")).isNull();
        assertThat(client.get(HBaseSettings.AUTHENTICATION)).isEqualTo("simple");
        assertThat(client.get("hadoop.security.authentication")).isEqualTo("simple");
        assertThat(client.get(HBaseSettings.MASTER_PRINCIPAL)).isNull();
        assertThat(client.getInt("hbase.client.retries.number", 0)).isEqualTo(3);
        assertThat(client.getInt("zookeeper.session.timeout", 0)).isEqualTo(30_000);
        assertThat(client.get("hbase.regionserver.port")).as("HBase's defaults are loaded").isEqualTo("16020");
    }

    @Test
    void readsWhatIsSet()
    {
        HBaseSettings settings = HBaseSettings.of(config(HBaseSettings.ZOOKEEPER_PORT, "2281", HBaseSettings.ZNODE, "/hbase-secure",
                HBaseSettings.TIMEOUT, "2000", HBaseSettings.PROTECTION, "Privacy"));
        assertThat(settings.zookeeperPort()).isEqualTo(2281);
        assertThat(settings.znode()).isEqualTo("/hbase-secure");
        assertThat(settings.protection()).isEqualTo("privacy");
        Configuration client = settings.client();
        assertThat(client.getInt(HBaseSettings.TIMEOUT, 0)).isEqualTo(2000);
        assertThat(client.getInt("zookeeper.session.timeout", 0)).as("ZooKeeper sessions last at least 10 s").isEqualTo(10_000);
        assertThat(client.get("hadoop.rpc.protection")).isEqualTo("privacy");
    }

    @Test
    void bootstrapServersReplaceZooKeeper()
    {
        ServiceConfig config = config(HBaseSettings.QUORUM, "", HBaseSettings.BOOTSTRAP, "hbase1:16020, hbase2:16000");
        assertThat(HBaseSettings.problems(config)).isEmpty();
        HBaseSettings settings = HBaseSettings.of(config);
        assertThat(settings.quorum()).isNull();
        assertThat(settings.bootstrap()).isEqualTo("hbase1:16020,hbase2:16000");
        Configuration client = settings.client();
        assertThat(client.get("hbase.client.registry.impl")).isEqualTo("org.apache.hadoop.hbase.client.RpcConnectionRegistry");
        assertThat(client.get(HBaseSettings.BOOTSTRAP)).isEqualTo("hbase1:16020,hbase2:16000");
        assertThat(client.get(HBaseSettings.QUORUM)).as("from hbase-default.xml, unused").isEqualTo("127.0.0.1");
    }

    @Test
    void kerberosTakesThePrincipalsRealmForTheServers()
    {
        HBaseSettings settings = HBaseSettings.of(kerberos());
        assertThat(settings.kerberos()).isTrue();
        assertThat(settings.password()).isEqualTo("secret");
        assertThat(settings.masterPrincipal()).isEqualTo("hbase/_HOST@EXAMPLE.COM");
        assertThat(settings.regionServerPrincipal()).isEqualTo("hbase/_HOST@EXAMPLE.COM");
        Configuration client = settings.client();
        assertThat(client.get(HBaseSettings.AUTHENTICATION)).isEqualTo("kerberos");
        assertThat(client.get("hadoop.security.authentication")).isEqualTo("kerberos");
        assertThat(client.get(HBaseSettings.MASTER_PRINCIPAL)).isEqualTo("hbase/_HOST@EXAMPLE.COM");
        HBaseSettings named = HBaseSettings.of(kerberos(HBaseSettings.MASTER_PRINCIPAL, "master/_HOST@EXAMPLE.COM", HBaseSettings.KEYTAB, "/etc/k",
                HBaseSettings.KDC, "kdc1:88"));
        assertThat(named.regionServerPrincipal()).isEqualTo("master/_HOST@EXAMPLE.COM");
        assertThat(named.keytab()).isEqualTo("/etc/k");
        assertThat(named.kdcs()).isEqualTo("kdc1:88");
        assertThat(HBaseSettings.of(kerberos(HBaseSettings.REGION_SERVER_PRINCIPAL, "rs/_HOST@EXAMPLE.COM")).regionServerPrincipal())
                .isEqualTo("rs/_HOST@EXAMPLE.COM");
    }

    @Test
    void namesWhatIsWrong()
    {
        assertThat(HBaseSettings.problems(config(HBaseSettings.QUORUM, ""))).singleElement().satisfies(problem -> {
            assertThat(problem.field()).isEqualTo(HBaseSettings.QUORUM);
            assertThat(problem.reason()).isEqualTo(ConfigProblem.Reason.REQUIRED);
        });
        assertThat(fields(config(HBaseSettings.QUORUM, "zk 1"))).containsExactly(HBaseSettings.QUORUM);
        assertThat(fields(config(HBaseSettings.QUORUM, "zk1:99999"))).containsExactly(HBaseSettings.QUORUM);
        assertThat(fields(config(HBaseSettings.BOOTSTRAP, "hbase1"))).containsExactly(HBaseSettings.BOOTSTRAP);
        assertThat(fields(config(HBaseSettings.ZOOKEEPER_PORT, "0x"))).containsExactly(HBaseSettings.ZOOKEEPER_PORT);
        assertThat(fields(config(HBaseSettings.ZOOKEEPER_PORT, "70000"))).containsExactly(HBaseSettings.ZOOKEEPER_PORT);
        assertThat(fields(config(HBaseSettings.ZNODE, "hbase"))).containsExactly(HBaseSettings.ZNODE);
        assertThat(fields(config(HBaseSettings.AUTHENTICATION, "token"))).containsExactly(HBaseSettings.AUTHENTICATION);
        assertThat(fields(config(HBaseSettings.USER, ""))).containsExactly(HBaseSettings.USER);
        assertThat(fields(config(HBaseSettings.PASSWORD, "x", HBaseSettings.KEYTAB, "/k", HBaseSettings.KDC, "kdc",
                HBaseSettings.MASTER_PRINCIPAL, "m", HBaseSettings.REGION_SERVER_PRINCIPAL, "r"))).as("Kerberos fields without Kerberos")
                .containsExactlyInAnyOrder(HBaseSettings.PASSWORD, HBaseSettings.KEYTAB, HBaseSettings.KDC, HBaseSettings.MASTER_PRINCIPAL,
                        HBaseSettings.REGION_SERVER_PRINCIPAL);
        assertThat(fields(config(HBaseSettings.PROTECTION, "secret"))).containsExactly(HBaseSettings.PROTECTION);
        for (String timeout : List.of("0", "600001", "soon")) {
            assertThat(fields(config(HBaseSettings.TIMEOUT, timeout))).containsExactly(HBaseSettings.TIMEOUT);
        }
        assertThatThrownBy(() -> HBaseSettings.of(config(HBaseSettings.TIMEOUT, "0"))).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("hbase.rpc.timeout: hbase.rpc.timeout is a whole number from 1 to 600000");
    }

    @Test
    void kerberosNeedsARealmACredentialAndServerPrincipals()
    {
        assertThat(fields(kerberos(HBaseSettings.USER, "grantforge"))).containsExactly(HBaseSettings.USER);
        assertThat(fields(kerberos(HBaseSettings.PASSWORD, ""))).containsExactly(HBaseSettings.PASSWORD);
        assertThat(fields(kerberos(HBaseSettings.PASSWORD, "", HBaseSettings.KEYTAB, "/etc/k"))).isEmpty();
        assertThat(fields(kerberos(HBaseSettings.KDC, "kdc1:notaport"))).containsExactly(HBaseSettings.KDC);
        assertThat(fields(kerberos(HBaseSettings.MASTER_PRINCIPAL, "hbase@EXAMPLE.COM", HBaseSettings.REGION_SERVER_PRINCIPAL, "hbase/host")))
                .containsExactly(HBaseSettings.MASTER_PRINCIPAL, HBaseSettings.REGION_SERVER_PRINCIPAL);
        assertThat(fields(kerberos(HBaseSettings.USER, ""))).containsExactly(HBaseSettings.USER);
    }
}

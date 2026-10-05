// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.hdfs;

import org.apache.hadoop.conf.Configuration;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class HadoopClientTest
{
    @Test
    void readsAdditionalPropertiesLineByLine()
    {
        assertThat(HadoopClient.properties(null)).isEmpty();
        assertThat(HadoopClient.properties("""
                # the nameservice
                dfs.nameservices = ns1

                dfs.ha.namenodes.ns1=nn1,nn2
                dfs.namenode.rpc-address.ns1.nn1=a:8020=x
                """)).containsExactly(Map.entry("dfs.nameservices", "ns1"), Map.entry("dfs.ha.namenodes.ns1", "nn1,nn2"),
                Map.entry("dfs.namenode.rpc-address.ns1.nn1", "a:8020=x"));
        assertThatIllegalArgumentException().isThrownBy(() -> HadoopClient.properties("a=b\n=c")).withMessageContaining("line 2");
        assertThatIllegalArgumentException().isThrownBy(() -> HadoopClient.properties("plain")).withMessageContaining("line 1");
    }

    @Test
    void turnsTheServiceIntoAHadoopConfiguration()
    {
        Configuration hadoop = new HadoopClient(HdfsProviderTest.config("hdfs://ns1", "hadoop.security.authentication", "kerberos",
                "dfs.namenode.kerberos.principal", " nn/_HOST@EXAMPLE.COM ", "hadoop.rpc.protection", "privacy",
                "hadoop.security.auth_to_local", " ", "hadoop.config", "dfs.nameservices=ns1\nipc.client.connect.max.retries=5"))
                .configuration();

        assertThat(hadoop.get("fs.defaultFS")).isEqualTo("hdfs://ns1");
        assertThat(hadoop.get("hadoop.security.authentication")).isEqualTo("kerberos");
        assertThat(hadoop.get("dfs.namenode.kerberos.principal")).isEqualTo("nn/_HOST@EXAMPLE.COM");
        assertThat(hadoop.get("hadoop.rpc.protection")).isEqualTo("privacy");
        assertThat(hadoop.get("dfs.nameservices")).isEqualTo("ns1");
        // The service's own properties win over the fast-failing defaults.
        assertThat(hadoop.get("ipc.client.connect.max.retries")).isEqualTo("5");
        assertThat(hadoop.get("ipc.client.connect.timeout")).isEqualTo("5000");
        assertThat(hadoop.get("fs.hdfs.impl.disable.cache")).isEqualTo("true");
    }
}

// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.hdfs;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.security.UserGroupInformation;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
        assertThatIllegalArgumentException().isThrownBy(() -> HadoopClient.properties("a=b\na=c"))
                .withMessageContaining("line 2").withMessageContaining("repeats property a");
        assertThatIllegalArgumentException().isThrownBy(() -> HadoopClient.properties("a b=c"))
                .withMessageContaining("line 1").withMessageContaining("whitespace");
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

    @Test
    void defaultsToSimpleAuthenticationAndKeepsAdditionalSettingsAuthoritative()
    {
        Configuration defaults = new HadoopClient(HdfsProviderTest.config("hdfs://ns1")).configuration();
        assertThat(defaults.get(HdfsProvider.AUTHENTICATION)).isEqualTo("simple");
        assertThat(defaults.get(HdfsProvider.AUTHORIZATION)).isEqualTo("false");

        Configuration overridden = new HadoopClient(HdfsProviderTest.config("hdfs://ns1", HdfsProvider.EXTRA,
                "hadoop.security.authentication=kerberos\nhadoop.security.authorization=true\nfs.defaultFS=hdfs://ns2"))
                .configuration();
        assertThat(overridden.get(HdfsProvider.AUTHENTICATION)).isEqualTo("kerberos");
        assertThat(overridden.get(HdfsProvider.AUTHORIZATION)).isEqualTo("true");
        assertThat(overridden.get("fs.defaultFS")).isEqualTo("hdfs://ns2");
    }

    @Test
    void rejectsUnsupportedEffectiveAuthentication()
    {
        HadoopClient client = new HadoopClient(HdfsProviderTest.config("file:///", HdfsProvider.EXTRA,
                "hadoop.security.authentication=unknown"));

        assertThatThrownBy(() -> client.run(files -> true)).isInstanceOf(IOException.class)
                .hasMessageContaining("unsupported Hadoop authentication type: unknown");
    }

    @Test
    void cancelsAnInterruptedCallAndPreservesTheInterrupt()
    {
        HadoopClient client = new HadoopClient(HdfsProviderTest.config("file:///"));
        Thread.currentThread().interrupt();
        try {
            assertThatThrownBy(() -> client.run(files -> true)).isInstanceOf(IOException.class)
                    .hasCauseInstanceOf(InterruptedException.class);
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
        }
        finally {
            Thread.interrupted();
        }
    }

    @Test
    void isolatesTheSecurityConfigurationUntilTheFileSystemActionFinishes() throws Exception
    {
        ExecutorService workers = Executors.newFixedThreadPool(2);
        CountDownLatch insideFirst = new CountDownLatch(1);
        CountDownLatch finishFirst = new CountDownLatch(1);
        CountDownLatch startedSecond = new CountDownLatch(1);
        try {
            Future<String> first = workers.submit(() -> new HadoopClient(HdfsProviderTest.config("file:///", "username", "first"))
                    .run(files -> {
                        insideFirst.countDown();
                        try {
                            if (!finishFirst.await(5, TimeUnit.SECONDS)) {
                                throw new IOException("the first call was not released");
                            }
                        }
                        catch (InterruptedException interrupted) {
                            Thread.currentThread().interrupt();
                            throw new IOException("interrupted in the first call", interrupted);
                        }
                        return UserGroupInformation.getCurrentUser().getUserName();
                    }));
            assertThat(insideFirst.await(5, TimeUnit.SECONDS)).isTrue();
            Future<String> second = workers.submit(() -> {
                startedSecond.countDown();
                return new HadoopClient(HdfsProviderTest.config("file:///", "username", "second"))
                        .run(files -> UserGroupInformation.getCurrentUser().getUserName());
            });
            assertThat(startedSecond.await(5, TimeUnit.SECONDS)).isTrue();
            assertThatThrownBy(() -> second.get(200, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
            finishFirst.countDown();
            assertThat(first.get(5, TimeUnit.SECONDS)).isEqualTo("first");
            assertThat(second.get(5, TimeUnit.SECONDS)).isEqualTo("second");
        }
        finally {
            finishFirst.countDown();
            workers.shutdownNow();
            assertThat(workers.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }
}

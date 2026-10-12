// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.plugin.api;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KerberosRealmsTest
{
    @Test
    void readsKdcsWithOptionalPorts()
    {
        assertThat(KerberosRealms.kdcs(" kdc1.example.com , kdc2.example.com:88,[2001:db8::1]:750,[::1],10.0.0.5"))
                .containsExactly("kdc1.example.com", "kdc2.example.com:88", "[2001:db8::1]:750", "[::1]", "10.0.0.5");
        for (String invalid : List.of("", "kdc1,,kdc2", "kdc:0", "kdc:65536", "kdc:", "kdc:8a", "::1", "kdc one", "-kdc", "tcp/kdc:88")) {
            assertThatThrownBy(() -> KerberosRealms.kdcs(invalid)).as(invalid).isInstanceOf(IllegalArgumentException.class)
                    .hasMessageStartingWith("not a KDC host[:port]");
        }
    }

    @Test
    void takesTheRealmFromThePrincipal()
    {
        assertThat(KerberosRealms.realm("grantforge@ALPHA.EXAMPLE")).isEqualTo("ALPHA.EXAMPLE");
        assertThat(KerberosRealms.realm("hdfs/namenode@BETA-1.EXAMPLE")).isEqualTo("BETA-1.EXAMPLE");
        for (String invalid : List.of("grantforge", "grantforge@", "@ALPHA.EXAMPLE", "grantforge@ALPHA EXAMPLE")) {
            assertThatThrownBy(() -> KerberosRealms.realm(invalid)).as(invalid).isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("must name its realm");
        }
    }

    @Test
    void writesShortNameRulesForTheRealmAlone()
    {
        assertThat(KerberosRealms.shortNames("BETA.EXAMPLE")).isEqualTo("RULE:[1:$1@$0](.*@BETA\\.EXAMPLE)s/@.*//\n"
                + "RULE:[2:$1@$0](.*@BETA\\.EXAMPLE)s/@.*//\nDEFAULT");
        assertThat(KerberosRealms.realmOf("grantforge")).isNull();
    }

    @Test
    void writesTheRealmsAfterTheServersOwnFile()
    {
        Map<String, List<String>> realms = new LinkedHashMap<>();
        realms.put("ALPHA.EXAMPLE", List.of("kdc1.alpha:88", "kdc2.alpha"));
        realms.put("BETA.EXAMPLE", List.of("kdc.beta"));
        Path base = Path.of("/etc/krb5.conf");

        assertThat(KerberosRealms.render(base, null, realms)).isEqualTo("""
                # Written by GrantForge from the KDCs of its services; changes are overwritten.
                include /etc/krb5.conf
                [realms]
                  ALPHA.EXAMPLE = {
                    kdc = kdc1.alpha:88
                    kdc = kdc2.alpha
                  }
                  BETA.EXAMPLE = {
                    kdc = kdc.beta
                  }
                """);
        assertThat(KerberosRealms.render(null, "ALPHA.EXAMPLE", Map.of("ALPHA.EXAMPLE", List.of("kdc")))).isEqualTo("""
                # Written by GrantForge from the KDCs of its services; changes are overwritten.
                [libdefaults]
                  default_realm = ALPHA.EXAMPLE
                [realms]
                  ALPHA.EXAMPLE = {
                    kdc = kdc
                  }
                """);
    }

    @Test
    void declaresRealmsInAFileOfTheProcess() throws IOException
    {
        String before = System.getProperty(KerberosRealms.CONFIGURATION);
        try {
            KerberosRealms.declare("ALPHA.EXAMPLE", List.of("kdc.alpha:88"));
            Path file = Path.of(System.getProperty(KerberosRealms.CONFIGURATION));
            assertThat(Files.readString(file)).contains("ALPHA.EXAMPLE = {", "kdc = kdc.alpha:88");
            long written = Files.getLastModifiedTime(file).toMillis();
            KerberosRealms.declare("ALPHA.EXAMPLE", List.of("kdc.alpha:88"));
            assertThat(Files.getLastModifiedTime(file).toMillis()).as("unchanged realms are not written again").isEqualTo(written);
            KerberosRealms.declare("BETA.EXAMPLE", List.of("kdc.beta"));
            assertThat(Files.readString(file)).contains("ALPHA.EXAMPLE = {", "BETA.EXAMPLE = {", "kdc = kdc.beta");
            System.setProperty("java.security.krb5.kdc", "kdc.elsewhere");
            assertThatThrownBy(() -> KerberosRealms.declare("GAMMA.EXAMPLE", List.of("kdc.gamma"))).isInstanceOf(IOException.class)
                    .hasMessageContaining("java.security.krb5.realm and .kdc");
        }
        finally {
            System.clearProperty("java.security.krb5.kdc");
            KerberosRealms.forget();
        }
        assertThat(System.getProperty(KerberosRealms.CONFIGURATION)).isEqualTo(before);
    }
}

// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.zookeeper;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import javax.security.auth.login.AppConfigurationEntry;
import javax.security.auth.login.Configuration;

import java.util.Map;

import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;

class ZooKeeperJaasTest
{
    @Test
    void isolatesBothThePrincipalAndTheKeytabBetweenServices()
    {
        Configuration previous = Configuration.getConfiguration();
        try {
            Configuration.setConfiguration(new ExternalConfiguration());
            String alice = ZooKeeperJaas.section("alice@EXAMPLE.COM", "/etc/alice.keytab");
            String bob = ZooKeeperJaas.section("bob@EXAMPLE.COM", "/etc/alice.keytab");
            String rotated = ZooKeeperJaas.section("alice@EXAMPLE.COM", "/etc/alice-next.keytab");
            assertThat(alice).startsWith(ZooKeeperJaas.PREFIX).isNotEqualTo(bob).isNotEqualTo(rotated);
            assertThat(bob).isNotEqualTo(rotated);
            Configuration installed = Configuration.getConfiguration();
            assertIdentity(installed, alice, "alice@EXAMPLE.COM", "/etc/alice.keytab");
            assertIdentity(installed, bob, "bob@EXAMPLE.COM", "/etc/alice.keytab");
            assertIdentity(installed, rotated, "alice@EXAMPLE.COM", "/etc/alice-next.keytab");
        }
        finally {
            Configuration.setConfiguration(previous);
        }
    }

    @Test
    void reusesASectionAndItsInstalledConfigurationForRepeatedSignIns()
    {
        Configuration previous = Configuration.getConfiguration();
        try {
            Configuration.setConfiguration(new ExternalConfiguration());
            String first = ZooKeeperJaas.section("repeat@EXAMPLE.COM", "/etc/repeat.keytab");
            Configuration installed = Configuration.getConfiguration();
            AppConfigurationEntry original = entries(installed, first)[0];
            assertThat(ZooKeeperJaas.section("repeat@EXAMPLE.COM", "/etc/repeat.keytab")).isEqualTo(first);
            assertThat(Configuration.getConfiguration()).isSameAs(installed);
            assertThat(entries(installed, first)[0]).isSameAs(original);
            ZooKeeperJaas.section("another@EXAMPLE.COM", "/etc/another.keytab");
            assertThat(Configuration.getConfiguration()).isSameAs(installed);
            assertIdentity(installed, first, "repeat@EXAMPLE.COM", "/etc/repeat.keytab");
        }
        finally {
            Configuration.setConfiguration(previous);
        }
    }

    @Test
    void preservesExternalSectionsAndDelegatesRefreshToTheirConfiguration()
    {
        Configuration previous = Configuration.getConfiguration();
        try {
            ExternalConfiguration external = new ExternalConfiguration();
            Configuration.setConfiguration(external);
            String own = ZooKeeperJaas.section("refresh@EXAMPLE.COM", "/etc/refresh.keytab");
            Configuration installed = Configuration.getConfiguration();
            assertThat(entries(installed, "ExternalService")).isSameAs(external.entries);
            assertThat(entries(installed, "ExternalService")[0].getOptions().get("generation")).isEqualTo("original");
            assertThat(installed.getAppConfigurationEntry("UnconfiguredService")).isNull();
            installed.refresh();
            assertThat(external.refreshes).isEqualTo(1);
            assertThat(entries(installed, "ExternalService")).isSameAs(external.entries);
            assertThat(entries(installed, "ExternalService")[0].getOptions().get("generation")).isEqualTo("refreshed");
            assertIdentity(installed, own, "refresh@EXAMPLE.COM", "/etc/refresh.keytab");
        }
        finally {
            Configuration.setConfiguration(previous);
        }
    }

    @Test
    void protectsCachedLoginEntriesFromMutatingTheReturnedArray()
    {
        Configuration previous = Configuration.getConfiguration();
        try {
            Configuration.setConfiguration(new ExternalConfiguration());
            String name = ZooKeeperJaas.section("copy@EXAMPLE.COM", "/etc/copy.keytab");
            Configuration installed = Configuration.getConfiguration();
            AppConfigurationEntry[] exposed = entries(installed, name);
            AppConfigurationEntry original = exposed[0];
            exposed[0] = new AppConfigurationEntry("UntrustedLoginModule", AppConfigurationEntry.LoginModuleControlFlag.REQUIRED,
                    Map.of("principal", "someone-else@EXAMPLE.COM"));
            AppConfigurationEntry[] fresh = entries(installed, name);
            assertThat(fresh).isNotSameAs(exposed).containsExactly(original);
            assertIdentity(installed, name, "copy@EXAMPLE.COM", "/etc/copy.keytab");
        }
        finally {
            Configuration.setConfiguration(previous);
        }
    }

    private static void assertIdentity(Configuration configuration, String name, String principal, String keytab)
    {
        AppConfigurationEntry entry = entries(configuration, name)[0];
        assertThat(entry.getLoginModuleName()).isEqualTo("com.sun.security.auth.module.Krb5LoginModule");
        assertThat(entry.getControlFlag()).isEqualTo(AppConfigurationEntry.LoginModuleControlFlag.REQUIRED);
        assertThat(entry.getOptions().get("principal")).isEqualTo(principal);
        assertThat(entry.getOptions().get("keyTab")).isEqualTo(keytab);
        assertThat(entry.getOptions().get("useKeyTab")).isEqualTo("true");
        assertThat(entry.getOptions().get("useTicketCache")).isEqualTo("false");
        assertThat(entry.getOptions().get("doNotPrompt")).isEqualTo("true");
    }

    private static AppConfigurationEntry[] entries(Configuration configuration, String name)
    {
        AppConfigurationEntry[] entries = requireNonNull(configuration.getAppConfigurationEntry(name), "expected JAAS section " + name);
        assertThat(entries).hasSize(1);
        return entries;
    }

    private static final class ExternalConfiguration
            extends Configuration
    {
        private AppConfigurationEntry[] entries = externalEntries("original");
        private int refreshes;

        @Override
        public AppConfigurationEntry @Nullable [] getAppConfigurationEntry(String name)
        {
            return "ExternalService".equals(name) ? entries : null;
        }

        @Override
        public void refresh()
        {
            refreshes++;
            entries = externalEntries("refreshed");
        }

        private static AppConfigurationEntry[] externalEntries(String generation)
        {
            return new AppConfigurationEntry[] {new AppConfigurationEntry("ExternalLoginModule",
                    AppConfigurationEntry.LoginModuleControlFlag.OPTIONAL, Map.of("generation", generation))};
        }
    }
}

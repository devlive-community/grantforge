// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.zookeeper;

import org.jspecify.annotations.Nullable;

import javax.security.auth.login.AppConfigurationEntry;
import javax.security.auth.login.Configuration;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * The JAAS login sections the services' Kerberos sign-ins use. ZooKeeper's client signs in through a JAAS section it is
 * told the name of, which the JDK looks up in one configuration for the whole process; each principal and keytab gets a
 * section of its own in a configuration that answers every other name as the process's configuration did, so one
 * GrantForge reaches ensembles signed in as different principals, and anything else in the process signs in unchanged.
 */
final class ZooKeeperJaas
{
    /** Names of the sections, followed by a digest of the principal and keytab. */
    static final String PREFIX = "GrantForgeZooKeeper-";

    private static final String KRB5 = "com.sun.security.auth.module.Krb5LoginModule";

    /** Guards installing the configuration; the JDK keeps one for the whole process. */
    private static final ReentrantLock LOCK = new ReentrantLock();

    /** The sections handed out; the configuration answers from it. */
    private static final Map<String, AppConfigurationEntry[]> SECTIONS = new ConcurrentHashMap<>();

    /** Whether this plugin's configuration is the process's, guarded by {@link #LOCK}. */
    private static final Installed INSTALLED = new Installed();

    private ZooKeeperJaas()
    {
    }

    /**
     * Returns the section that signs in as a principal with a keytab, adding it the first time.
     *
     * @param principal the principal, such as grantforge@EXAMPLE.COM
     * @param keytab the path of its keytab on the GrantForge server
     * @return the section's name, for ZooKeeper's {@code zookeeper.sasl.clientconfig}
     */
    static String section(String principal, String keytab)
    {
        String name = PREFIX + digest(principal + "\n" + keytab);
        SECTIONS.computeIfAbsent(name, ignored -> new AppConfigurationEntry[] {new AppConfigurationEntry(KRB5,
                AppConfigurationEntry.LoginModuleControlFlag.REQUIRED, Map.of("useKeyTab", "true", "keyTab", keytab, "principal", principal,
                "storeKey", "true", "useTicketCache", "false", "doNotPrompt", "true", "refreshKrb5Config", "true", "isInitiator", "true"))});
        install();
        return name;
    }

    /** Makes this plugin's configuration the process's, around whichever was in place, unless it is already. */
    private static void install()
    {
        LOCK.lock();
        try {
            Configuration current = currentOrNull();
            if (current instanceof Sections) {
                return;
            }
            Configuration.setConfiguration(new Sections(current));
            INSTALLED.done = true;
        }
        finally {
            LOCK.unlock();
        }
    }

    private static @Nullable Configuration currentOrNull()
    {
        try {
            return Configuration.getConfiguration();
        }
        catch (SecurityException none) {
            // No JAAS file is configured for the process: there is nothing to answer other names with.
            return null;
        }
    }

    /**
     * Whether a section was installed by this plugin.
     *
     * @return {@code true} once a Kerberos service has signed in
     */
    static boolean installed()
    {
        LOCK.lock();
        try {
            return INSTALLED.done;
        }
        finally {
            LOCK.unlock();
        }
    }

    private static String digest(String text)
    {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)), 0, 12);
        }
        catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("every JDK has SHA-256", impossible);
        }
    }

    /** Whether the configuration is installed; kept in one holder, as the process keeps one configuration. */
    private static final class Installed
    {
        private boolean done;
    }

    /** The services' sections, in front of the configuration the process had. */
    private static final class Sections
            extends Configuration
    {
        private final @Nullable Configuration previous;

        Sections(@Nullable Configuration previous)
        {
            this.previous = previous;
        }

        @Override
        public AppConfigurationEntry @Nullable [] getAppConfigurationEntry(String name)
        {
            AppConfigurationEntry[] own = SECTIONS.get(name);
            if (own != null) {
                return own.clone();
            }
            return previous == null ? null : previous.getAppConfigurationEntry(name);
        }

        @Override
        public void refresh()
        {
            if (previous != null) {
                previous.refresh();
            }
        }
    }
}

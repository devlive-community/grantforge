// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.hdfs;

import org.jspecify.annotations.Nullable;

import javax.security.auth.Subject;
import javax.security.auth.login.AppConfigurationEntry;
import javax.security.auth.login.LoginContext;
import javax.security.auth.login.LoginException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.regex.Pattern;

import static java.util.Objects.requireNonNull;

/**
 * The KDCs services name for their realms, so that one GrantForge reaches clusters of several realms. The JDK reads
 * Kerberos settings once for the whole process, from one krb5.conf; the services' realms go into a file of their own,
 * which includes the server's krb5.conf, and becomes the process's. Every Kerberos sign-in reads that file again, so a
 * new or changed realm takes effect without a restart.
 */
final class KerberosRealms
{
    static final String CONFIGURATION = "java.security.krb5.conf";

    private static final Pattern REALM = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]*");
    private static final Pattern HOST = Pattern.compile("[A-Za-z0-9]([A-Za-z0-9.-]*[A-Za-z0-9])?|\\[[0-9A-Fa-f:.]+]");
    private static final Pattern DEFAULT_REALM = Pattern.compile("(?m)^\\s*default_realm\\s*=");

    /** Guards the realms and the file; the JDK's Kerberos settings belong to the whole process. */
    private static final ReentrantLock LOCK = new ReentrantLock();

    /** Guarded by {@link #LOCK}. */
    private static final State STATE = new State();

    private KerberosRealms()
    {
    }

    /**
     * Reads a service's KDCs.
     *
     * @param text {@code host[:port]} entries, separated by commas
     * @return the KDCs in their order
     * @throws IllegalArgumentException for an empty list or an entry that is not a host with an optional port
     */
    static List<String> kdcs(String text)
    {
        List<String> kdcs = new ArrayList<>();
        for (String entry : text.split(",", -1)) {
            String kdc = entry.strip();
            int colon = kdc.lastIndexOf(':');
            boolean ported = colon > kdc.lastIndexOf(']');
            String host = ported ? kdc.substring(0, colon) : kdc;
            if (!HOST.matcher(host).matches() || ported && !port(kdc.substring(colon + 1))) {
                throw new IllegalArgumentException("not a KDC host[:port]: " + (kdc.isEmpty() ? "an empty entry" : kdc));
            }
            kdcs.add(kdc);
        }
        return kdcs;
    }

    private static boolean port(String text)
    {
        if (text.isEmpty() || text.length() > 5 || !text.chars().allMatch(Character::isDigit)) {
            return false;
        }
        int port = Integer.parseInt(text);
        return port > 0 && port <= 65_535;
    }

    /**
     * The realm of a principal.
     *
     * @param principal such as {@code grantforge@EXAMPLE.COM}
     * @return the realm
     * @throws IllegalArgumentException if the principal names no realm
     */
    static String realm(String principal)
    {
        String realm = realmOf(principal);
        if (realm == null) {
            throw new IllegalArgumentException("the username must name its realm, such as grantforge@EXAMPLE.COM");
        }
        return realm;
    }

    /**
     * The realm of a principal, if it names one.
     *
     * @param principal such as {@code grantforge@EXAMPLE.COM}
     * @return the realm, or {@code null}
     */
    static @Nullable String realmOf(String principal)
    {
        int at = principal.lastIndexOf('@');
        String realm = at < 0 ? "" : principal.substring(at + 1);
        return at <= 0 || !REALM.matcher(realm).matches() ? null : realm;
    }

    /**
     * Rules that give a realm's users and services their short names, as Hadoop needs of every principal it signs in
     * and its {@code DEFAULT} rule does only for the default realm.
     *
     * @param realm the realm
     * @return the rules, one per line, ending with {@code DEFAULT}
     */
    static String shortNames(String realm)
    {
        String quoted = realm.replace(".", "\\.");
        return "RULE:[1:$1@$0](.*@" + quoted + ")s/@.*//\nRULE:[2:$1@$0](.*@" + quoted + ")s/@.*//\nDEFAULT";
    }

    /**
     * Makes the process reach a realm through these KDCs.
     *
     * @param realm the realm
     * @param kdcs its KDCs
     * @throws IOException if the server's Kerberos settings leave no room for them, or the file cannot be written
     */
    static void declare(String realm, List<String> kdcs) throws IOException
    {
        LOCK.lock();
        try {
            if (System.getProperty("java.security.krb5.realm") != null || System.getProperty("java.security.krb5.kdc") != null) {
                throw new IOException("the GrantForge server sets java.security.krb5.realm and .kdc, which replace every krb5.conf;"
                        + " remove them to give services their own KDCs");
            }
            Path file = STATE.written;
            if (kdcs.equals(STATE.realms.get(realm)) && file != null && file.toString().equals(System.getProperty(CONFIGURATION))) {
                return;
            }
            if (file == null) {
                STATE.base = existing(System.getProperty(CONFIGURATION));
                if (STATE.base == null) {
                    STATE.base = existing(Path.of(System.getProperty("java.home"), "conf", "security", "krb5.conf").toString());
                }
                if (STATE.base == null) {
                    STATE.base = existing("/etc/krb5.conf");
                }
                file = Files.createTempFile("grantforge-hdfs-krb5-", ".conf");
                file.toFile().deleteOnExit();
                STATE.written = file;
            }
            STATE.realms.put(realm, List.copyOf(kdcs));
            if (STATE.defaultRealm == null) {
                STATE.defaultRealm = realm;
            }
            Path baseFile = STATE.base;
            String included = baseFile == null ? null : Files.readString(baseFile, StandardCharsets.UTF_8);
            String ownDefault = included != null && DEFAULT_REALM.matcher(included).find() ? null : STATE.defaultRealm;
            String text = render(baseFile, ownDefault, STATE.realms);
            Path next = Files.createTempFile(requireNonNull(file.getParent(), "a temporary file lies in a directory"),
                    "grantforge-hdfs-krb5-", ".next");
            Files.writeString(next, text, StandardCharsets.UTF_8);
            Files.move(next, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            System.setProperty(CONFIGURATION, file.toString());
            reload();
        }
        finally {
            LOCK.unlock();
        }
    }

    private static @Nullable Path existing(@Nullable String path)
    {
        if (path == null || path.isBlank()) {
            return null;
        }
        Path file = Path.of(path).toAbsolutePath();
        return Files.isRegularFile(file) && !file.equals(STATE.written) ? file : null;
    }

    /**
     * The krb5.conf of the realms.
     *
     * @param base the server's own krb5.conf, included first, or {@code null}
     * @param defaultRealm the default realm to set, when the server's file has none, or {@code null}
     * @param realms the realms with their KDCs
     * @return the file's text
     */
    static String render(@Nullable Path base, @Nullable String defaultRealm, Map<String, List<String>> realms)
    {
        StringBuilder text = new StringBuilder(512).append("# Written by GrantForge from the KDCs of its HDFS services; changes are overwritten.\n");
        if (base != null) {
            text.append("include ").append(base.toAbsolutePath()).append('\n');
        }
        if (defaultRealm != null) {
            text.append("[libdefaults]\n  default_realm = ").append(defaultRealm).append('\n');
        }
        text.append("[realms]\n");
        realms.forEach((realm, kdcs) -> {
            text.append("  ").append(realm).append(" = {\n");
            kdcs.forEach(kdc -> text.append("    kdc = ").append(kdc).append('\n'));
            text.append("  }\n");
        });
        return text.toString();
    }

    /**
     * Has the JDK read the file again now: Hadoop asks it for the default realm before any sign-in would. The JDK has
     * no API for this but its login module's {@code refreshKrb5Config}, which this sign-in without credentials uses,
     * failing as it must once the file is read.
     */
    private static void reload()
    {
        Map<String, String> options = Map.of("refreshKrb5Config", "true", "doNotPrompt", "true", "useTicketCache", "false",
                "useKeyTab", "false", "principal", "grantforge-krb5-reload");
        javax.security.auth.login.Configuration jaas = new javax.security.auth.login.Configuration()
        {
            @Override
            public AppConfigurationEntry[] getAppConfigurationEntry(String name)
            {
                return new AppConfigurationEntry[] {new AppConfigurationEntry("com.sun.security.auth.module.Krb5LoginModule",
                        AppConfigurationEntry.LoginModuleControlFlag.REQUIRED, options)};
            }
        };
        try {
            new LoginContext("grantforge-krb5-reload", new Subject(), callbacks -> { }, jaas).login();
        }
        catch (LoginException expected) {
            // Without a password or keytab the sign-in cannot succeed; reading the file was all it was for.
        }
    }

    /** Forgets the realms and gives the process back its own krb5.conf; for tests. */
    @SuppressWarnings("PMD.NullAssignment") // forgetting the written file is what a test needs
    static void forget()
    {
        LOCK.lock();
        try {
            Path baseFile = STATE.base;
            if (STATE.written != null) {
                if (baseFile == null) {
                    System.clearProperty(CONFIGURATION);
                }
                else {
                    System.setProperty(CONFIGURATION, baseFile.toString());
                }
            }
            STATE.realms.clear();
            STATE.written = null;
            STATE.base = null;
            STATE.defaultRealm = null;
        }
        finally {
            LOCK.unlock();
        }
    }

    /** What the process was given: the services' realms, the file written for them, and what that file builds on. */
    private static final class State
    {
        final Map<String, List<String>> realms = new TreeMap<>();
        @Nullable Path written;
        @Nullable Path base;
        @Nullable String defaultRealm;
    }
}

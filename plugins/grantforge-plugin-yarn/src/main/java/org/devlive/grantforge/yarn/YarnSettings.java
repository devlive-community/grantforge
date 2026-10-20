// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.yarn;

import org.apache.hadoop.conf.Configuration;
import org.devlive.grantforge.plugin.api.KerberosRealms;
import org.devlive.grantforge.plugin.api.ServiceConfig;
import org.devlive.grantforge.plugin.api.model.ConfigProblem;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The settings of a YARN service, checked, and the client configuration they make. Field names are Hadoop's own where
 * Hadoop has one ({@code yarn.resourcemanager.address}, {@code hadoop.security.authentication},
 * {@code yarn.resourcemanager.principal}…); the sign-in has fields of its own: a user for simple authentication, a
 * principal with a keytab or its password for Kerberos. Naming several ResourceManagers makes the client fail over
 * between them, as YARN's own high availability does.
 *
 * @param resourceManagers the ResourceManagers' client addresses, {@code host:port}
 * @param kerberos whether the cluster signs clients in with Kerberos
 * @param user the user, or with Kerberos the principal
 * @param password the principal's Kerberos password, or {@code null}
 * @param keytab the principal's keytab on the GrantForge server, or {@code null}
 * @param kdcs the KDCs of the principal's realm, or {@code null} for the server's krb5.conf
 * @param principal the ResourceManagers' Kerberos principal, with {@code _HOST} for their host names
 * @param protection the RPC protection: authentication, integrity or privacy
 * @param timeout how long a request may take, in milliseconds
 */
record YarnSettings(List<String> resourceManagers, boolean kerberos, String user, @Nullable String password, @Nullable String keytab,
        @Nullable String kdcs, String principal, String protection, int timeout)
{
    static final String RESOURCE_MANAGER = "yarn.resourcemanager.address";
    static final String AUTHENTICATION = "hadoop.security.authentication";
    static final String USER = "username";
    static final String PASSWORD = "password";
    static final String KEYTAB = "keytab";
    static final String KDC = "kerberos.kdc";
    static final String PRINCIPAL = "yarn.resourcemanager.principal";
    static final String PROTECTION = "hadoop.rpc.protection";
    static final String TIMEOUT = "timeout";

    static final String SIMPLE = "simple";
    static final String KERBEROS = "kerberos";
    static final List<String> AUTHENTICATIONS = List.of(SIMPLE, KERBEROS);
    static final List<String> PROTECTIONS = List.of("authentication", "integrity", "privacy");
    static final int DEFAULT_PORT = 8032;
    static final int DEFAULT_TIMEOUT = 30_000;

    private static final Pattern HOST = Pattern.compile("(\\[[0-9A-Fa-f:.]+]|[A-Za-z0-9._-]+)(?::(\\d{1,5}))?");

    /**
     * Checks a service's settings.
     *
     * @param config the settings
     * @return what is wrong, by field; empty when they are usable
     */
    static List<ConfigProblem> problems(ServiceConfig config)
    {
        List<ConfigProblem> problems = new ArrayList<>();
        String managers = text(config, RESOURCE_MANAGER);
        if (managers == null) {
            problems.add(new ConfigProblem(RESOURCE_MANAGER, ConfigProblem.Reason.REQUIRED,
                    "name the ResourceManager, such as rm1.example.com:8032, or every ResourceManager of a highly available cluster"));
        }
        else if (addresses(managers).isEmpty()) {
            problems.add(invalid(RESOURCE_MANAGER, "list the ResourceManagers as host or host:port separated by commas, such as rm1:8032,rm2:8032"));
        }
        String authentication = lower(config, AUTHENTICATION, SIMPLE);
        if (!AUTHENTICATIONS.contains(authentication)) {
            problems.add(invalid(AUTHENTICATION, "hadoop.security.authentication is simple or kerberos"));
        }
        String user = text(config, USER);
        if (user == null) {
            problems.add(new ConfigProblem(USER, ConfigProblem.Reason.REQUIRED, "name the user that looks up queues and applications"));
        }
        if (KERBEROS.equals(authentication)) {
            kerberos(config, user, problems);
        }
        else {
            for (String field : List.of(PASSWORD, KEYTAB, KDC, PRINCIPAL)) {
                if (text(config, field) != null) {
                    problems.add(invalid(field, "only Kerberos uses " + field));
                }
            }
        }
        if (!PROTECTIONS.contains(lower(config, PROTECTION, "authentication"))) {
            problems.add(invalid(PROTECTION, "hadoop.rpc.protection is authentication, integrity or privacy"));
        }
        try {
            timeout(config);
        }
        catch (IllegalArgumentException wrong) {
            problems.add(invalid(TIMEOUT, String.valueOf(wrong.getMessage())));
        }
        return problems;
    }

    private static void kerberos(ServiceConfig config, @Nullable String user, List<ConfigProblem> problems)
    {
        if (user != null) {
            try {
                KerberosRealms.realm(user);
            }
            catch (IllegalArgumentException wrong) {
                problems.add(invalid(USER, String.valueOf(wrong.getMessage())));
            }
        }
        if (text(config, PASSWORD) == null && text(config, KEYTAB) == null) {
            problems.add(new ConfigProblem(PASSWORD, ConfigProblem.Reason.REQUIRED, "Kerberos needs the principal's password or a keytab"));
        }
        String kdcs = text(config, KDC);
        if (kdcs != null) {
            try {
                KerberosRealms.kdcs(kdcs);
            }
            catch (IllegalArgumentException wrong) {
                problems.add(invalid(KDC, String.valueOf(wrong.getMessage())));
            }
        }
        String principal = text(config, PRINCIPAL);
        if (principal != null && !principal.matches("[^/@\\s]+/[^/@\\s]+@[^/@\\s]+")) {
            problems.add(invalid(PRINCIPAL, "a server principal is service/host@REALM, such as yarn/_HOST@EXAMPLE.COM"));
        }
    }

    /** The addresses of a list, each with its port; none when one is not an address. */
    private static List<String> addresses(String value)
    {
        List<String> addresses = new ArrayList<>();
        for (String entry : value.split(",", -1)) {
            Matcher match = HOST.matcher(entry.strip());
            if (!match.matches()) {
                return List.of();
            }
            int port = match.group(2) == null ? DEFAULT_PORT : port(match.group(2));
            if (port == 0) {
                return List.of();
            }
            String address = match.group(1) + ":" + port;
            if (!addresses.contains(address)) {
                addresses.add(address);
            }
        }
        return addresses;
    }

    private static int port(String value)
    {
        int port = Integer.parseInt(value);
        return port >= 1 && port <= 65_535 ? port : 0;
    }

    private static ConfigProblem invalid(String field, String message)
    {
        return new ConfigProblem(field, ConfigProblem.Reason.INVALID, message);
    }

    /**
     * Reads checked settings.
     *
     * @param config the settings
     * @return them
     * @throws IllegalArgumentException when they are not usable, naming the first problem
     */
    static YarnSettings of(ServiceConfig config)
    {
        List<ConfigProblem> problems = problems(config);
        if (!problems.isEmpty()) {
            ConfigProblem first = problems.get(0);
            throw new IllegalArgumentException(first.field() + ": " + first.detail());
        }
        String user = String.valueOf(text(config, USER));
        boolean kerberos = KERBEROS.equals(lower(config, AUTHENTICATION, SIMPLE));
        return new YarnSettings(List.copyOf(addresses(String.valueOf(text(config, RESOURCE_MANAGER)))), kerberos, user, kerberos ? text(config, PASSWORD) : null,
                kerberos ? text(config, KEYTAB) : null, kerberos ? text(config, KDC) : null,
                kerberos ? textOr(config, PRINCIPAL, "yarn/_HOST@" + KerberosRealms.realm(user)) : "", lower(config, PROTECTION, "authentication"),
                timeout(config));
    }

    /**
     * Reads the timeout.
     *
     * @param config the settings
     * @return milliseconds, {@value #DEFAULT_TIMEOUT} unless set
     * @throws IllegalArgumentException when it is no whole number from 1 to 600000
     */
    static int timeout(ServiceConfig config)
    {
        String value = text(config, TIMEOUT);
        if (value == null) {
            return DEFAULT_TIMEOUT;
        }
        int timeout = value.matches("\\d{1,6}") ? Integer.parseInt(value) : 0;
        if (timeout >= 1 && timeout <= 600_000) {
            return timeout;
        }
        throw new IllegalArgumentException("timeout is a whole number of milliseconds from 1 to 600000");
    }

    /**
     * Builds the client configuration: Hadoop's and YARN's defaults and the service's settings, but no
     * {@code core-site.xml} or {@code yarn-site.xml} the class path may hold, so a service is exactly what its settings
     * say. Requests give up after the timeout instead of YARN's quarter of an hour; with several ResourceManagers the
     * client fails over between them.
     *
     * @return the configuration
     */
    // The defaults are in the plugin's own jars, which its class loader reads whatever thread asks.
    @SuppressWarnings("PMD.UseProperClassLoader")
    Configuration client()
    {
        Configuration yarn = new Configuration(false);
        yarn.setClassLoader(YarnSettings.class.getClassLoader());
        yarn.addResource("core-default.xml");
        yarn.addResource("yarn-default.xml");
        if (resourceManagers.size() == 1) {
            yarn.set(RESOURCE_MANAGER, resourceManagers.get(0));
        }
        else {
            List<String> ids = new ArrayList<>();
            for (int index = 0; index < resourceManagers.size(); index++) {
                String id = "rm" + (index + 1);
                ids.add(id);
                yarn.set(RESOURCE_MANAGER + "." + id, resourceManagers.get(index));
                yarn.set("yarn.resourcemanager.hostname." + id, host(resourceManagers.get(index)));
            }
            yarn.setBoolean("yarn.resourcemanager.ha.enabled", true);
            yarn.set("yarn.resourcemanager.ha.rm-ids", String.join(",", ids));
            yarn.setInt("yarn.client.failover-max-attempts", resourceManagers.size() * 2);
            yarn.setInt("yarn.client.failover-sleep-base-ms", 100);
            yarn.setInt("yarn.client.failover-sleep-max-ms", 1000);
        }
        yarn.setLong("yarn.resourcemanager.connect.max-wait.ms", timeout);
        yarn.setLong("yarn.resourcemanager.connect.retry-interval.ms", Math.min(1000, timeout));
        yarn.setInt("ipc.client.connect.timeout", Math.min(timeout, 20_000));
        yarn.setInt("ipc.client.connect.max.retries", 0);
        yarn.setInt("ipc.client.connect.max.retries.on.timeouts", 0);
        yarn.setInt("ipc.client.rpc-timeout.ms", timeout);
        yarn.set(PROTECTION, protection);
        yarn.set(AUTHENTICATION, kerberos ? KERBEROS : SIMPLE);
        if (kerberos) {
            yarn.set(PRINCIPAL, principal);
        }
        return yarn;
    }

    private static String host(String address)
    {
        return address.substring(0, address.lastIndexOf(':'));
    }

    private static @Nullable String text(ServiceConfig config, String field)
    {
        String value = config.get(field);
        return value == null || value.isBlank() ? null : value.strip();
    }

    private static String textOr(ServiceConfig config, String field, String fallback)
    {
        String value = text(config, field);
        return value == null ? fallback : value;
    }

    private static String lower(ServiceConfig config, String field, String fallback)
    {
        return textOr(config, field, fallback).toLowerCase(Locale.ROOT);
    }
}

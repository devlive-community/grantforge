// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.hbase;

import org.apache.hadoop.conf.Configuration;
import org.devlive.grantforge.plugin.api.KerberosRealms;
import org.devlive.grantforge.plugin.api.ServiceConfig;
import org.devlive.grantforge.plugin.api.model.ConfigProblem;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * The settings of an HBase service, checked, and the client configuration they make. Field names are HBase's own client
 * settings where HBase has one ({@code hbase.zookeeper.quorum}, {@code hbase.security.authentication},
 * {@code hbase.master.kerberos.principal}…); the sign-in has fields of its own: a user for simple authentication, a
 * principal with a keytab or its password for Kerberos. The cluster is found through ZooKeeper, or, from HBase 2.5 on,
 * through bootstrap servers without ZooKeeper.
 *
 * @param quorum the ZooKeeper hosts, {@code host[:port]} separated by commas, or {@code null} with bootstrap servers
 * @param zookeeperPort the ZooKeeper port of hosts that name none
 * @param znode the cluster's parent znode
 * @param bootstrap the masters or region servers to start from, {@code host:port} separated by commas, or {@code null}
 * @param kerberos whether the cluster signs clients in with Kerberos
 * @param user the user, or with Kerberos the principal
 * @param password the principal's Kerberos password, or {@code null}
 * @param keytab the principal's keytab on the GrantForge server, or {@code null}
 * @param kdcs the KDCs of the principal's realm, or {@code null} for the server's krb5.conf
 * @param masterPrincipal the masters' Kerberos principal, with {@code _HOST} for their host names
 * @param regionServerPrincipal the region servers' Kerberos principal
 * @param protection the RPC protection: authentication, integrity or privacy
 * @param timeout the RPC timeout in milliseconds
 */
record HBaseSettings(@Nullable String quorum, int zookeeperPort, String znode, @Nullable String bootstrap, boolean kerberos, String user,
        @Nullable String password, @Nullable String keytab, @Nullable String kdcs, String masterPrincipal, String regionServerPrincipal,
        String protection, int timeout)
{
    static final String QUORUM = "hbase.zookeeper.quorum";
    static final String ZOOKEEPER_PORT = "hbase.zookeeper.property.clientPort";
    static final String ZNODE = "zookeeper.znode.parent";
    static final String BOOTSTRAP = "hbase.client.bootstrap.servers";
    static final String AUTHENTICATION = "hbase.security.authentication";
    static final String USER = "username";
    static final String PASSWORD = "password";
    static final String KEYTAB = "keytab";
    static final String KDC = "kerberos.kdc";
    static final String MASTER_PRINCIPAL = "hbase.master.kerberos.principal";
    static final String REGION_SERVER_PRINCIPAL = "hbase.regionserver.kerberos.principal";
    static final String PROTECTION = "hbase.rpc.protection";
    static final String TIMEOUT = "hbase.rpc.timeout";

    static final String SIMPLE = "simple";
    static final String KERBEROS = "kerberos";
    static final List<String> AUTHENTICATIONS = List.of(SIMPLE, KERBEROS);
    static final List<String> PROTECTIONS = List.of("authentication", "integrity", "privacy");
    static final int DEFAULT_ZOOKEEPER_PORT = 2181;
    static final String DEFAULT_ZNODE = "/hbase";
    static final int DEFAULT_TIMEOUT = 30_000;

    private static final Pattern HOST = Pattern.compile("(?:\\[[0-9A-Fa-f:.]+]|[A-Za-z0-9._-]+)(?::(\\d{1,5}))?");
    private static final Pattern ZNODE_PATH = Pattern.compile("(/[A-Za-z0-9._-]+)+");

    /**
     * Checks a service's settings.
     *
     * @param config the settings
     * @return what is wrong, by field; empty when they are usable
     */
    static List<ConfigProblem> problems(ServiceConfig config)
    {
        List<ConfigProblem> problems = new ArrayList<>();
        String quorum = text(config, QUORUM);
        String bootstrap = text(config, BOOTSTRAP);
        if (quorum == null && bootstrap == null) {
            problems.add(new ConfigProblem(QUORUM, ConfigProblem.Reason.REQUIRED,
                    "name the ZooKeeper quorum, or the bootstrap servers of an HBase 2.5 or later cluster"));
        }
        if (quorum != null && !hosts(quorum, false)) {
            problems.add(invalid(QUORUM, "list the ZooKeeper hosts as host or host:port separated by commas, such as zk1,zk2:2181"));
        }
        if (bootstrap != null && !hosts(bootstrap, true)) {
            problems.add(invalid(BOOTSTRAP, "list the masters or region servers as host:port separated by commas, such as hbase1:16020"));
        }
        String port = text(config, ZOOKEEPER_PORT);
        if (port != null && port(port) == 0) {
            problems.add(invalid(ZOOKEEPER_PORT, "the ZooKeeper port is a number from 1 to 65535"));
        }
        String znode = text(config, ZNODE);
        if (znode != null && !ZNODE_PATH.matcher(znode).matches()) {
            problems.add(invalid(ZNODE, "the parent znode is an absolute path, such as /hbase"));
        }
        String authentication = lower(config, AUTHENTICATION, SIMPLE);
        if (!AUTHENTICATIONS.contains(authentication)) {
            problems.add(invalid(AUTHENTICATION, "hbase.security.authentication is simple or kerberos"));
        }
        String user = text(config, USER);
        if (user == null) {
            problems.add(new ConfigProblem(USER, ConfigProblem.Reason.REQUIRED, "name the user that looks up namespaces and tables"));
        }
        if (KERBEROS.equals(authentication)) {
            kerberos(config, user, problems);
        }
        else {
            for (String field : List.of(PASSWORD, KEYTAB, KDC, MASTER_PRINCIPAL, REGION_SERVER_PRINCIPAL)) {
                if (text(config, field) != null) {
                    problems.add(invalid(field, "only Kerberos uses " + field));
                }
            }
        }
        if (!PROTECTIONS.contains(lower(config, PROTECTION, "authentication"))) {
            problems.add(invalid(PROTECTION, "hbase.rpc.protection is authentication, integrity or privacy"));
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
        for (String field : List.of(MASTER_PRINCIPAL, REGION_SERVER_PRINCIPAL)) {
            String principal = text(config, field);
            if (principal != null && !principal.matches("[^/@\\s]+/[^/@\\s]+@[^/@\\s]+")) {
                problems.add(invalid(field, "a server principal is service/host@REALM, such as hbase/_HOST@EXAMPLE.COM"));
            }
        }
    }

    private static boolean hosts(String value, boolean portRequired)
    {
        for (String host : value.split(",", -1)) {
            java.util.regex.Matcher match = HOST.matcher(host.strip());
            if (!match.matches() || match.group(1) == null && portRequired || match.group(1) != null && port(match.group(1)) == 0) {
                return false;
            }
        }
        return true;
    }

    private static int port(String value)
    {
        int port = value.matches("\\d{1,5}") ? Integer.parseInt(value) : 0;
        return port <= 65_535 ? port : 0;
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
    static HBaseSettings of(ServiceConfig config)
    {
        List<ConfigProblem> problems = problems(config);
        if (!problems.isEmpty()) {
            ConfigProblem first = problems.get(0);
            throw new IllegalArgumentException(first.field() + ": " + first.detail());
        }
        String user = String.valueOf(text(config, USER));
        boolean kerberos = KERBEROS.equals(lower(config, AUTHENTICATION, SIMPLE));
        String port = text(config, ZOOKEEPER_PORT);
        String master = kerberos ? textOr(config, MASTER_PRINCIPAL, "hbase/_HOST@" + KerberosRealms.realm(user)) : "";
        String quorum = text(config, QUORUM);
        String bootstrap = text(config, BOOTSTRAP);
        return new HBaseSettings(quorum == null ? null : quorum.replace(" ", ""), port == null ? DEFAULT_ZOOKEEPER_PORT : port(port),
                textOr(config, ZNODE, DEFAULT_ZNODE), bootstrap == null ? null : bootstrap.replace(" ", ""), kerberos, user,
                kerberos ? text(config, PASSWORD) : null, kerberos ? text(config, KEYTAB) : null, kerberos ? text(config, KDC) : null, master,
                kerberos ? textOr(config, REGION_SERVER_PRINCIPAL, master) : "", lower(config, PROTECTION, "authentication"), timeout(config));
    }

    /**
     * Reads the RPC timeout.
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
        throw new IllegalArgumentException("hbase.rpc.timeout is a whole number from 1 to 600000");
    }

    /**
     * Builds the client configuration: Hadoop's and HBase's defaults and the service's settings, but no
     * {@code core-site.xml} or {@code hbase-site.xml} the class path may hold, so a service is exactly what its settings
     * say; lookups give up after a few attempts instead of HBase's minutes.
     *
     * @return the configuration
     */
    // The defaults are in the plugin's own jars, which its class loader reads whatever thread asks.
    @SuppressWarnings("PMD.UseProperClassLoader")
    Configuration client()
    {
        Configuration hbase = new Configuration(false);
        hbase.setClassLoader(HBaseSettings.class.getClassLoader());
        hbase.addResource("core-default.xml");
        hbase.addResource("hbase-default.xml");
        if (bootstrap != null) {
            hbase.set("hbase.client.registry.impl", "org.apache.hadoop.hbase.client.RpcConnectionRegistry");
            hbase.set(BOOTSTRAP, bootstrap);
        }
        else {
            hbase.set(QUORUM, String.valueOf(quorum));
            hbase.setInt(ZOOKEEPER_PORT, zookeeperPort);
            hbase.set(ZNODE, znode);
            hbase.setInt("zookeeper.recovery.retry", 1);
            hbase.setInt("zookeeper.session.timeout", Math.max(timeout, 10_000));
        }
        hbase.setInt(TIMEOUT, timeout);
        hbase.setInt("hbase.client.operation.timeout", timeout);
        hbase.setInt("hbase.client.meta.operation.timeout", timeout);
        hbase.setInt("hbase.client.retries.number", 3);
        hbase.setInt("hbase.client.pause", 200);
        hbase.set(PROTECTION, protection);
        hbase.set("hadoop.rpc.protection", protection);
        String authentication = kerberos ? KERBEROS : SIMPLE;
        hbase.set(AUTHENTICATION, authentication);
        hbase.set("hadoop.security.authentication", authentication);
        if (kerberos) {
            hbase.set(MASTER_PRINCIPAL, masterPrincipal);
            hbase.set(REGION_SERVER_PRINCIPAL, regionServerPrincipal);
        }
        return hbase;
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

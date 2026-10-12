// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.zookeeper;

import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.ZooDefs;
import org.apache.zookeeper.ZooKeeper;
import org.apache.zookeeper.data.ACL;
import org.apache.zookeeper.data.Stat;
import org.devlive.grantforge.plugin.api.BrowseEntry;
import org.devlive.grantforge.plugin.api.BrowsePage;
import org.devlive.grantforge.plugin.api.BrowseRequest;
import org.devlive.grantforge.plugin.api.ConnectionResult;
import org.devlive.grantforge.plugin.api.LookupException;
import org.devlive.grantforge.plugin.api.LookupRequest;
import org.devlive.grantforge.plugin.api.ServiceConfig;
import org.devlive.grantforge.plugin.api.ServiceTypeProvider;
import org.devlive.grantforge.plugin.api.model.AccessTypeDefinition;
import org.devlive.grantforge.plugin.api.model.ConfigField;
import org.devlive.grantforge.plugin.api.model.ConfigFieldType;
import org.devlive.grantforge.plugin.api.model.ConfigProblem;
import org.devlive.grantforge.plugin.api.model.MatcherType;
import org.devlive.grantforge.plugin.api.model.ResourceDefinition;
import org.devlive.grantforge.plugin.api.model.ServiceTypeDefinition;
import org.jspecify.annotations.Nullable;

import javax.net.ssl.SSLException;
import javax.security.auth.login.LoginException;
import javax.security.sasl.SaslException;

import java.io.IOException;
import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * The ZooKeeper service type: znode paths, matched as paths and optionally with everything below them, with ZooKeeper's
 * own permissions, read, write, create, delete and admin. A service names its ensemble as ZooKeeper's clients do,
 * {@code zk1:2181,zk2:2181,zk3:2181/chroot}, and the lookup user signs in with digest authentication or Kerberos (SASL)
 * over plain or TLS connections. Connections, znode lookups and browsing go through ZooKeeper's own client.
 */
public final class ZooKeeperProvider
        implements ServiceTypeProvider
{
    /** The service type's name. */
    public static final String TYPE = "zookeeper";

    static final String PATH = "path";
    static final String CONNECT = "zookeeper.connect";
    static final String AUTHENTICATION = "authentication";
    static final String USER = "username";
    static final String PASSWORD = "password";
    static final String KEYTAB = "keytab";
    static final String SERVER_PRINCIPAL = "zookeeper.server.principal";
    static final String SERVICE_NAME = "zookeeper.sasl.client.username";
    static final String SECURE = "zookeeper.client.secure";
    static final String TRUSTSTORE = "zookeeper.ssl.trustStore.location";
    static final String TRUSTSTORE_PASSWORD = "zookeeper.ssl.trustStore.password";
    static final String TRUSTSTORE_TYPE = "zookeeper.ssl.trustStore.type";
    static final String KEYSTORE = "zookeeper.ssl.keyStore.location";
    static final String KEYSTORE_PASSWORD = "zookeeper.ssl.keyStore.password";
    static final String KEYSTORE_TYPE = "zookeeper.ssl.keyStore.type";
    static final String SESSION_TIMEOUT = "session.timeout.ms";
    static final String CONNECT_TIMEOUT = "connection.timeout.ms";
    static final String LOOKUP_ROOT = "lookup.path";
    static final String LOOKUP_MAX = "lookup.max.entries";
    static final String NONE = "none";
    static final String DIGEST = "digest";
    static final String KERBEROS = "kerberos";

    private static final Set<String> AUTHENTICATIONS = Set.of(NONE, DIGEST, KERBEROS);
    private static final Pattern HOST = Pattern.compile("[A-Za-z0-9]([A-Za-z0-9.-]*[A-Za-z0-9])?|\\[[0-9A-Fa-f:.]+]");

    @Override
    public ServiceTypeDefinition definition()
    {
        return ServiceTypeDefinition.builder(TYPE).label("ZooKeeper").description("Znodes of an Apache ZooKeeper ensemble")
                .resources(ResourceDefinition.builder(PATH).label("Znode path").matcher(MatcherType.PATH).caseSensitive(true)
                        .recursiveSupported(true).excludesSupported(true).lookupSupported(true).browseSupported(true).validLeaf(true)
                        .build())
                .accessTypes(AccessTypeDefinition.of("read", "Read"), AccessTypeDefinition.of("write", "Write"),
                        AccessTypeDefinition.of("create", "Create"), AccessTypeDefinition.of("delete", "Delete"),
                        AccessTypeDefinition.of("admin", "Admin"))
                .configFields(
                        ConfigField.builder(CONNECT).label("Connection string").type(ConfigFieldType.STRING).mandatory()
                                .description("The ensemble's servers, host:port separated by commas, with an optional chroot,"
                                        + " such as zk1:2181,zk2:2181,zk3:2181/kafka").build(),
                        ConfigField.builder(AUTHENTICATION).label("Authentication type").type(ConfigFieldType.ENUM)
                                .options(NONE, DIGEST, KERBEROS).defaultValue(NONE).build(),
                        ConfigField.builder(USER).label("Username").type(ConfigFieldType.STRING)
                                .description("Digest: the user of the digest ACLs; Kerberos: the principal, such as grantforge@EXAMPLE.COM")
                                .build(),
                        ConfigField.builder(PASSWORD).label("Password").type(ConfigFieldType.SECRET)
                                .description("The digest user's password").build(),
                        ConfigField.builder(KEYTAB).label("Keytab").type(ConfigFieldType.STRING)
                                .description("Kerberos: path of the principal's keytab on the GrantForge server").build(),
                        ConfigField.builder(SERVICE_NAME).label("Server service name").type(ConfigFieldType.STRING).defaultValue("zookeeper")
                                .description("Kerberos: the first part of the servers' principals, zookeeper/<host>@<REALM>").build(),
                        ConfigField.builder(SERVER_PRINCIPAL).label("Server principal").type(ConfigFieldType.STRING)
                                .description("Kerberos: the servers' whole principal where it is not <service name>/<host>@<REALM>,"
                                        + " such as zookeeper/zk.example.com@EXAMPLE.COM").build(),
                        ConfigField.builder(SECURE).label("TLS").type(ConfigFieldType.BOOLEAN).defaultValue("false")
                                .description("Connect to the servers' secure client port").build(),
                        ConfigField.builder(TRUSTSTORE).label("TLS truststore").type(ConfigFieldType.STRING)
                                .description("Path on the GrantForge server of the truststore that verifies the servers;"
                                        + " empty trusts what the server's Java trusts").build(),
                        ConfigField.builder(TRUSTSTORE_PASSWORD).label("TLS truststore password").type(ConfigFieldType.SECRET).build(),
                        ConfigField.builder(TRUSTSTORE_TYPE).label("TLS truststore type").type(ConfigFieldType.ENUM)
                                .options("jks", "pkcs12", "pem").defaultValue("jks").build(),
                        ConfigField.builder(KEYSTORE).label("TLS keystore").type(ConfigFieldType.STRING)
                                .description("Path on the GrantForge server of the client certificate, for servers that require one")
                                .build(),
                        ConfigField.builder(KEYSTORE_PASSWORD).label("TLS keystore password").type(ConfigFieldType.SECRET).build(),
                        ConfigField.builder(KEYSTORE_TYPE).label("TLS keystore type").type(ConfigFieldType.ENUM)
                                .options("jks", "pkcs12", "pem").defaultValue("jks").build(),
                        ConfigField.builder(SESSION_TIMEOUT).label("Session timeout (ms)").type(ConfigFieldType.INTEGER).defaultValue("15000")
                                .description("Between 2000 and 120000").build(),
                        ConfigField.builder(CONNECT_TIMEOUT).label("Connection timeout (ms)").type(ConfigFieldType.INTEGER).defaultValue("10000")
                                .description("How long to wait for a server and the sign-in; between 1000 and 60000").build(),
                        ConfigField.builder(LOOKUP_ROOT).label("Lookup znode").type(ConfigFieldType.STRING).defaultValue("/")
                                .description("Absolute znode to test and browse, below the chroot, such as /brokers; lookup stays below it")
                                .build(),
                        ConfigField.builder(LOOKUP_MAX).label("Maximum children").type(ConfigFieldType.INTEGER).defaultValue("10000")
                                .description("Stop with an error above this many children of one znode; between 1 and 100000").build())
                .build();
    }

    @Override
    public List<ConfigProblem> validateConfig(ServiceConfig config)
    {
        List<ConfigProblem> problems = new ArrayList<>();
        String connect = config.get(CONNECT);
        if (connect != null) {
            try {
                connectString(connect);
            }
            catch (IllegalArgumentException invalid) {
                problems.add(new ConfigProblem(CONNECT, ConfigProblem.Reason.INVALID, invalid.getMessage()));
            }
        }
        String authentication = authentication(config);
        if (!AUTHENTICATIONS.contains(authentication)) {
            problems.add(new ConfigProblem(AUTHENTICATION, ConfigProblem.Reason.INVALID, "authentication must be none, digest or kerberos"));
        }
        if ((DIGEST.equals(authentication) || KERBEROS.equals(authentication)) && blank(config.get(USER))) {
            problems.add(new ConfigProblem(USER, ConfigProblem.Reason.REQUIRED,
                    DIGEST.equals(authentication) ? "digest authentication needs a user" : "Kerberos needs a principal"));
        }
        if (DIGEST.equals(authentication)) {
            if (blank(config.get(PASSWORD))) {
                problems.add(new ConfigProblem(PASSWORD, ConfigProblem.Reason.REQUIRED, "digest authentication needs a password"));
            }
            String user = config.get(USER);
            if (user != null && user.contains(":")) {
                problems.add(new ConfigProblem(USER, ConfigProblem.Reason.INVALID, "a digest user cannot contain a colon"));
            }
        }
        if (KERBEROS.equals(authentication) && blank(config.get(KEYTAB))) {
            problems.add(new ConfigProblem(KEYTAB, ConfigProblem.Reason.REQUIRED, "Kerberos needs the principal's keytab"));
        }
        if (!KERBEROS.equals(authentication)) {
            for (String field : List.of(KEYTAB, SERVER_PRINCIPAL)) {
                if (!blank(config.get(field))) {
                    problems.add(new ConfigProblem(field, ConfigProblem.Reason.INVALID, "used only with Kerberos"));
                }
            }
        }
        range(config, SESSION_TIMEOUT, 15_000, 2_000, 120_000, problems);
        range(config, CONNECT_TIMEOUT, 10_000, 1_000, 60_000, problems);
        try {
            lookupRoot(config);
        }
        catch (IllegalArgumentException invalid) {
            problems.add(new ConfigProblem(LOOKUP_ROOT, ConfigProblem.Reason.INVALID, invalid.getMessage()));
        }
        range(config, LOOKUP_MAX, 10_000, 1, 100_000, problems);
        return problems;
    }

    /**
     * Reads a connection string: servers as {@code host[:port]} separated by commas, then an optional chroot.
     *
     * @param text the connection string
     * @return the servers, as given
     * @throws IllegalArgumentException when a server or the chroot is not valid
     */
    static List<String> connectString(String text)
    {
        String value = text.strip();
        int slash = value.indexOf('/');
        String servers = slash < 0 ? value : value.substring(0, slash);
        if (slash >= 0) {
            String chroot = value.substring(slash);
            if (chroot.length() > 1 && !chroot(chroot)) {
                throw new IllegalArgumentException("the chroot must be an absolute znode path without a trailing slash, such as /kafka");
            }
        }
        List<String> hosts = new ArrayList<>();
        for (String entry : servers.split(",", -1)) {
            String server = entry.strip();
            int colon = server.lastIndexOf(':');
            boolean ported = colon > server.lastIndexOf(']');
            String host = ported ? server.substring(0, colon) : server;
            if (!HOST.matcher(host).matches() || (ported && !port(server.substring(colon + 1)))) {
                throw new IllegalArgumentException("not a server host[:port]: " + (server.isEmpty() ? "an empty entry" : server));
            }
            hosts.add(server);
        }
        return hosts;
    }

    private static boolean chroot(String chroot)
    {
        try {
            return normalizePath(chroot).equals(chroot);
        }
        catch (IllegalArgumentException invalid) {
            return false;
        }
    }

    private static boolean port(String text)
    {
        if (text.isEmpty() || text.length() > 5 || !text.chars().allMatch(Character::isDigit)) {
            return false;
        }
        int port = Integer.parseInt(text);
        return port > 0 && port <= 65_535;
    }

    private static String authentication(ServiceConfig config)
    {
        String value = config.get(AUTHENTICATION);
        return value == null || value.isBlank() ? NONE : value.strip().toLowerCase(Locale.ROOT);
    }

    private static void range(ServiceConfig config, String field, long fallback, long low, long high, List<ConfigProblem> problems)
    {
        try {
            long value = config.getLong(field, fallback);
            if (value < low || value > high) {
                problems.add(new ConfigProblem(field, ConfigProblem.Reason.INVALID, "must be between " + low + " and " + high));
            }
        }
        catch (IllegalArgumentException invalid) {
            problems.add(new ConfigProblem(field, ConfigProblem.Reason.INVALID, "must be a whole number"));
        }
    }

    private static String lookupRoot(ServiceConfig config)
    {
        String root = config.get(LOOKUP_ROOT);
        return normalizePath(root == null || root.isBlank() ? "/" : root.strip());
    }

    /**
     * Normalizes a znode path: absolute, without empty segments; {@code .} and {@code ..} are not znode names.
     *
     * @param path the path
     * @return the path, {@code /} or without a trailing slash
     * @throws IllegalArgumentException when it is not an absolute znode path
     */
    static String normalizePath(String path)
    {
        if (!path.startsWith("/") || path.indexOf('\0') >= 0) {
            throw new IllegalArgumentException("use an absolute znode path, such as /brokers");
        }
        List<String> components = new ArrayList<>();
        for (String component : path.split("/", -1)) {
            if (".".equals(component) || "..".equals(component)) {
                throw new IllegalArgumentException("znode paths cannot contain . or .. segments");
            }
            if (!component.isEmpty()) {
                components.add(component);
            }
        }
        return "/" + String.join("/", components);
    }

    private static int scanLimit(ServiceConfig config)
    {
        return (int) config.getLong(LOOKUP_MAX, 10_000);
    }

    private static boolean blank(@Nullable String value)
    {
        return value == null || value.isBlank();
    }

    @Override
    public ConnectionResult testConnection(ServiceConfig config)
    {
        try {
            String root = lookupRoot(config);
            return new ZooKeeperClient(config).run(zookeeper -> {
                if (zookeeper.exists(root, false) == null) {
                    return ConnectionResult.failed("the lookup znode does not exist: " + root);
                }
                // Reaching the ensemble alone does not prove this user can list znodes in the policy editor.
                ZooKeeperClient.children(zookeeper, root, scanLimit(config));
                return ConnectionResult.succeeded();
            });
        }
        catch (IOException | KeeperException | IllegalArgumentException failed) {
            return ConnectionResult.failed(message(failed));
        }
    }

    /**
     * Lists the znodes that start with what was typed: the children of the znode typed so far whose names start with the
     * rest, by name. A parent typed that does not exist has no children; the lookup znode itself missing, a refusal, an
     * unreachable ensemble, a failed sign-in or a znode over the scan limit fail with the reason named.
     */
    @Override
    public List<String> lookup(LookupRequest request)
    {
        if (!PATH.equals(request.resource())) {
            return List.of();
        }
        String root;
        String parent;
        String prefix;
        int maximum;
        try {
            root = lookupRoot(request.config());
            String typed = request.userInput().strip();
            String path = typed.isEmpty() ? root + "/" : typed.startsWith("/") ? typed : ("/".equals(root) ? "/" : root + "/") + typed;
            boolean children = path.endsWith("/");
            path = normalizePath(path);
            if (!within(path, root)) {
                throw new IllegalArgumentException("lookup must stay inside " + root);
            }
            if (children || path.equals(root)) {
                path = "/".equals(path) ? path : path + "/";
            }
            int slash = path.lastIndexOf('/');
            parent = slash == 0 ? "/" : path.substring(0, slash);
            prefix = path.substring(slash + 1);
            maximum = scanLimit(request.config());
        }
        catch (IllegalArgumentException invalid) {
            throw new LookupException(LookupException.Reason.INVALID_INPUT, String.valueOf(invalid.getMessage()), invalid);
        }
        String base = "/".equals(parent) ? "" : parent;
        List<String> names;
        try {
            names = new ZooKeeperClient(request.config()).run(zookeeper -> {
                try {
                    return ZooKeeperClient.children(zookeeper, parent, maximum);
                }
                catch (KeeperException.NoNodeException missing) {
                    if (parent.equals(root)) {
                        throw new LookupException(LookupException.Reason.NOT_FOUND, "the lookup znode does not exist: " + root, missing);
                    }
                    return List.of();
                }
            });
        }
        catch (IOException | KeeperException failed) {
            throw failure(failed);
        }
        return names.stream().filter(name -> name.startsWith(prefix)).limit(request.limit()).map(name -> base + "/" + name).toList();
    }

    /**
     * Lists one page of the children of a znode below the lookup znode, sorted by name, each with its ACL, the size of
     * its data and when it last changed; a znode with children of its own can be opened further.
     */
    @Override
    public BrowsePage browse(BrowseRequest request)
    {
        if (!PATH.equals(request.resource())) {
            throw new LookupException(LookupException.Reason.INVALID_INPUT, "only znode paths can be browsed");
        }
        String root;
        String directory;
        int maximum;
        try {
            root = lookupRoot(request.config());
            String asked = request.directory().strip();
            directory = asked.isEmpty() ? root : normalizePath(asked);
            if (!within(directory, root)) {
                throw new IllegalArgumentException("browsing must stay inside " + root);
            }
            maximum = scanLimit(request.config());
        }
        catch (IllegalArgumentException invalid) {
            throw new LookupException(LookupException.Reason.INVALID_INPUT, String.valueOf(invalid.getMessage()), invalid);
        }
        String base = "/".equals(directory) ? "" : directory;
        String after = request.cursor();
        List<BrowseEntry> entries = new ArrayList<>();
        boolean more;
        try {
            more = new ZooKeeperClient(request.config()).run(zookeeper -> {
                List<String> names;
                try {
                    names = ZooKeeperClient.children(zookeeper, directory, maximum);
                }
                catch (KeeperException.NoNodeException missing) {
                    throw new LookupException(LookupException.Reason.NOT_FOUND, "the znode does not exist: " + directory, missing);
                }
                List<String> page = names.stream().filter(name -> after == null || name.compareTo(after) > 0).toList();
                for (String name : page.subList(0, Math.min(page.size(), request.pageSize()))) {
                    BrowseEntry entry = entry(zookeeper, name, base + "/" + name);
                    if (entry != null) {
                        entries.add(entry);
                    }
                }
                return page.size() > request.pageSize();
            });
        }
        catch (IOException | KeeperException failed) {
            throw failure(failed);
        }
        String next = more && !entries.isEmpty() ? entries.get(entries.size() - 1).name() : null;
        return new BrowsePage(root, directory, entries, next);
    }

    /** One child, read with its ACL; a child that went away meanwhile is left out, one whose ACL is closed shows without it. */
    private static @Nullable BrowseEntry entry(ZooKeeper zookeeper, String name, String path) throws KeeperException, InterruptedException
    {
        Stat stat = new Stat();
        try {
            return entry(name, path, stat, acl(zookeeper.getACL(path, stat)));
        }
        catch (KeeperException.NoNodeException gone) {
            return null;
        }
        catch (KeeperException.NoAuthException closed) {
            return hidden(zookeeper, name, path);
        }
    }

    /**
     * A child whose ACL this user may not read: shown with what it may still be told, or with its name alone when
     * ZooKeeper does not even answer whether it exists; its parent listed it, so it is there, and opening it says why not.
     */
    private static @Nullable BrowseEntry hidden(ZooKeeper zookeeper, String name, String path) throws KeeperException, InterruptedException
    {
        try {
            Stat seen = zookeeper.exists(path, false);
            return seen == null ? null : entry(name, path, seen, null);
        }
        catch (KeeperException.NoAuthException closed) {
            return new BrowseEntry(name, path, true, null, null, null, null, null);
        }
    }

    private static BrowseEntry entry(String name, String path, Stat stat, @Nullable String permission)
    {
        boolean parent = stat.getNumChildren() > 0;
        return new BrowseEntry(name, path, parent, null, null, permission, parent ? null : (long) stat.getDataLength(),
                stat.getMtime() > 0 ? Instant.ofEpochMilli(stat.getMtime()) : null);
    }

    /**
     * An ACL as ZooKeeper's own tools write it: {@code scheme:id:cdrwa} entries, separated by commas.
     *
     * @param acl the entries
     * @return the text
     */
    static String acl(List<ACL> acl)
    {
        return acl.stream().map(entry -> entry.getId().getScheme() + ":" + entry.getId().getId() + ":" + perms(entry.getPerms()))
                .collect(Collectors.joining(", "));
    }

    private static String perms(int perms)
    {
        StringBuilder text = new StringBuilder();
        append(text, perms, ZooDefs.Perms.CREATE, 'c');
        append(text, perms, ZooDefs.Perms.DELETE, 'd');
        append(text, perms, ZooDefs.Perms.READ, 'r');
        append(text, perms, ZooDefs.Perms.WRITE, 'w');
        append(text, perms, ZooDefs.Perms.ADMIN, 'a');
        return text.toString();
    }

    private static void append(StringBuilder text, int perms, int perm, char letter)
    {
        if ((perms & perm) != 0) {
            text.append(letter);
        }
    }

    /** Whether a normalized path is the root or below it. */
    private static boolean within(String path, String root)
    {
        return "/".equals(root) || path.equals(root) || path.startsWith(root + "/");
    }

    /** Names why a lookup failed, from the first failure in the chain that tells. */
    static LookupException failure(Exception failed)
    {
        String text = message(failed);
        for (Throwable cause = failed; cause != null; cause = cause.getCause()) {
            LookupException.@Nullable Reason reason = reason(cause);
            if (reason != null) {
                return new LookupException(reason, text, failed);
            }
        }
        return new LookupException(LookupException.Reason.FAILED, text, failed);
    }

    private static LookupException.@Nullable Reason reason(Throwable cause)
    {
        if (cause instanceof TooManyChildrenException) {
            return LookupException.Reason.LIMIT_EXCEEDED;
        }
        if (cause instanceof ZooKeeperLoginException || cause instanceof KeeperException.AuthFailedException
                || cause instanceof SaslException || cause instanceof LoginException) {
            return LookupException.Reason.AUTHENTICATION_FAILED;
        }
        if (cause instanceof KeeperException.NoAuthException) {
            return LookupException.Reason.ACCESS_DENIED;
        }
        if (cause instanceof KeeperException.NoNodeException) {
            return LookupException.Reason.NOT_FOUND;
        }
        // Servers whose certificate cannot be verified are as good as unreachable: nothing may be asked of them.
        if (cause instanceof ConnectException || cause instanceof UnknownHostException || cause instanceof NoRouteToHostException
                || cause instanceof SocketTimeoutException || cause instanceof SSLException
                || cause instanceof KeeperException.ConnectionLossException || cause instanceof KeeperException.SessionExpiredException
                || cause instanceof KeeperException.OperationTimeoutException) {
            return LookupException.Reason.UNREACHABLE;
        }
        return null;
    }

    /** The first line of a failure, which is the useful part of its often long message. */
    private static String message(Exception failure)
    {
        String text = failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage();
        int line = text.indexOf('\n');
        return line < 0 ? text : text.substring(0, line);
    }
}

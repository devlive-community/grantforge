// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.hdfs;

import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.ipc.RemoteException;
import org.apache.hadoop.security.AccessControlException;
import org.apache.hadoop.security.authentication.client.AuthenticationException;
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

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.UnknownHostException;
import java.nio.file.AccessDeniedException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * The HDFS service type, modelled on Apache Ranger's: paths, matched as paths and optionally with everything below them,
 * with read, write and execute. A service names its cluster like Hadoop does, {@code hdfs://namenode:8020}, an HA
 * nameservice ({@code hdfs://nameservice1} with the nameservice's properties), or {@code webhdfs://}; the lookup user
 * signs in with simple authentication or Kerberos. Connections and path lookups go through Hadoop's own client.
 */
public final class HdfsProvider
        implements ServiceTypeProvider
{
    /** The service type's name. */
    public static final String TYPE = "hdfs";

    static final String PATH = "path";
    static final String USER = "username";
    static final String PASSWORD = "password";
    static final String KEYTAB = "keytab";
    static final String KDC = "kerberos.kdc";
    static final String DEFAULT_FS = "fs.default.name";
    static final String AUTHORIZATION = "hadoop.security.authorization";
    static final String AUTHENTICATION = "hadoop.security.authentication";
    static final String AUTH_TO_LOCAL = "hadoop.security.auth_to_local";
    static final String DATANODE_PRINCIPAL = "dfs.datanode.kerberos.principal";
    static final String NAMENODE_PRINCIPAL = "dfs.namenode.kerberos.principal";
    static final String SECONDARY_PRINCIPAL = "dfs.secondary.namenode.kerberos.principal";
    static final String RPC_PROTECTION = "hadoop.rpc.protection";
    static final String TRUSTSTORE = "ssl.client.truststore.location";
    static final String TRUSTSTORE_PASSWORD = "ssl.client.truststore.password";
    static final String TRUSTSTORE_TYPE = "ssl.client.truststore.type";
    static final String EXTRA = "hadoop.config";
    static final String LOOKUP_ROOT = "lookup.path";
    static final String LOOKUP_MAX = "lookup.max.entries";
    static final String SIMPLE = "simple";
    static final String KERBEROS = "kerberos";

    /** Settings passed to Hadoop under their own names. */
    static final List<String> HADOOP_SETTINGS = List.of(AUTHORIZATION, AUTHENTICATION, AUTH_TO_LOCAL, DATANODE_PRINCIPAL,
            NAMENODE_PRINCIPAL, SECONDARY_PRINCIPAL, RPC_PROTECTION);

    private static final Set<String> SCHEMES = Set.of("hdfs", "webhdfs", "swebhdfs", "viewfs");

    @Override
    public ServiceTypeDefinition definition()
    {
        return ServiceTypeDefinition.builder(TYPE).label("HDFS").description("Paths of the Hadoop Distributed File System")
                .resources(ResourceDefinition.builder(PATH).label("Path").matcher(MatcherType.PATH).caseSensitive(true)
                        .recursiveSupported(true).excludesSupported(true).lookupSupported(true).browseSupported(true).validLeaf(true)
                        .build())
                .accessTypes(AccessTypeDefinition.of("read", "Read"), AccessTypeDefinition.of("write", "Write"),
                        AccessTypeDefinition.of("execute", "Execute"))
                .configFields(
                        ConfigField.builder(USER).label("Username").type(ConfigFieldType.STRING).mandatory()
                                .description("Who lists paths; with Kerberos, the principal, such as grantforge@EXAMPLE.COM").build(),
                        ConfigField.builder(PASSWORD).label("Password").type(ConfigFieldType.SECRET)
                                .description("The principal's Kerberos password, unless a keytab is given").build(),
                        ConfigField.builder(KEYTAB).label("Keytab").type(ConfigFieldType.STRING)
                                .description("Path of the principal's keytab on the GrantForge server").build(),
                        ConfigField.builder(KDC).label("KDCs").type(ConfigFieldType.STRING)
                                .description("KDCs of the principal's realm, host[:port] separated by commas, such as"
                                        + " kdc1.example.com,kdc2.example.com:88; empty uses the server's krb5.conf").build(),
                        ConfigField.builder(DEFAULT_FS).label("Namenode URL").type(ConfigFieldType.STRING).mandatory()
                                .pattern("[A-Za-z][A-Za-z0-9+.-]*://\\S+")
                                .description("Hadoop 2.x: webhdfs://namenode:50070; Hadoop 3.x: hdfs://namenode:8020, hdfs://nameservice1 or webhdfs://namenode:9870").build(),
                        ConfigField.builder(AUTHORIZATION).label("Authorization enabled").type(ConfigFieldType.BOOLEAN)
                                .defaultValue("false").build(),
                        ConfigField.builder(AUTHENTICATION).label("Authentication type").type(ConfigFieldType.ENUM)
                                .options(SIMPLE, KERBEROS).defaultValue(SIMPLE).build(),
                        ConfigField.builder(AUTH_TO_LOCAL).label("Auth to local rules").type(ConfigFieldType.TEXT).build(),
                        ConfigField.builder(DATANODE_PRINCIPAL).label("DataNode principal").type(ConfigFieldType.STRING).build(),
                        ConfigField.builder(NAMENODE_PRINCIPAL).label("NameNode principal").type(ConfigFieldType.STRING).build(),
                        ConfigField.builder(SECONDARY_PRINCIPAL).label("Secondary NameNode principal").type(ConfigFieldType.STRING).build(),
                        ConfigField.builder(RPC_PROTECTION).label("RPC protection").type(ConfigFieldType.ENUM)
                                .options("authentication", "integrity", "privacy").defaultValue("authentication").build(),
                        ConfigField.builder(TRUSTSTORE).label("TLS truststore").type(ConfigFieldType.STRING)
                                .description("Path on the GrantForge server of the truststore that verifies swebhdfs:// NameNodes;"
                                        + " empty trusts what the server's Java trusts").build(),
                        ConfigField.builder(TRUSTSTORE_PASSWORD).label("TLS truststore password").type(ConfigFieldType.SECRET)
                                .description("Needed only when the truststore is protected").build(),
                        ConfigField.builder(TRUSTSTORE_TYPE).label("TLS truststore type").type(ConfigFieldType.ENUM)
                                .options("jks", "pkcs12").defaultValue("jks").build(),
                        ConfigField.builder(EXTRA).label("Additional Hadoop properties").type(ConfigFieldType.TEXT)
                                .description("One key=value per line, such as the HA nameservice: dfs.nameservices,"
                                        + " dfs.ha.namenodes.<ns>, dfs.namenode.rpc-address.<ns>.<nn>,"
                                        + " dfs.client.failover.proxy.provider.<ns>").build(),
                        ConfigField.builder(LOOKUP_ROOT).label("Lookup directory").type(ConfigFieldType.STRING).defaultValue("/")
                                .description("Absolute directory to test and browse, such as /data; lookup stays below this path").build(),
                        ConfigField.builder(LOOKUP_MAX).label("Maximum directory entries").type(ConfigFieldType.INTEGER).defaultValue("10000")
                                .description("Stop with an error above this many entries; between 1 and 100000").build())
                .build();
    }

    @Override
    public List<ConfigProblem> validateConfig(ServiceConfig config)
    {
        List<ConfigProblem> problems = new ArrayList<>();
        Map<String, String> extra;
        try {
            extra = HadoopClient.properties(config.get(EXTRA));
        }
        catch (IllegalArgumentException invalid) {
            problems.add(new ConfigProblem(EXTRA, ConfigProblem.Reason.INVALID, invalid.getMessage()));
            extra = Map.of();
        }
        String address = extra.getOrDefault("fs.defaultFS", extra.getOrDefault(DEFAULT_FS, config.get(DEFAULT_FS)));
        if (extra.containsKey("fs.defaultFS") && extra.containsKey(DEFAULT_FS)) {
            problems.add(new ConfigProblem(EXTRA, ConfigProblem.Reason.INVALID,
                    "fs.defaultFS and fs.default.name are aliases; configure only one"));
        }
        else if (address != null) {
            if (!validAddress(address)) {
                String field = extra.containsKey("fs.defaultFS") || extra.containsKey(DEFAULT_FS) ? EXTRA : DEFAULT_FS;
                problems.add(new ConfigProblem(field, ConfigProblem.Reason.INVALID,
                        "use a cluster URI (hdfs://, webhdfs://, swebhdfs:// or viewfs://), without credentials, a path, query or fragment"));
            }
        }
        String authentication = extra.getOrDefault(AUTHENTICATION, config.get(AUTHENTICATION));
        if (authentication != null && !Set.of(SIMPLE, KERBEROS).contains(authentication.strip())) {
            problems.add(new ConfigProblem(extra.containsKey(AUTHENTICATION) ? EXTRA : AUTHENTICATION,
                    ConfigProblem.Reason.INVALID, "authentication must be simple or kerberos"));
        }
        if (authentication != null && KERBEROS.equals(authentication.strip()) && blank(config.get(PASSWORD)) && blank(config.get(KEYTAB))) {
            problems.add(new ConfigProblem(PASSWORD, ConfigProblem.Reason.REQUIRED, "Kerberos needs a password or a keytab"));
        }
        String kdc = config.get(KDC);
        if (kdc != null && !kdc.isBlank()) {
            if (authentication == null || !KERBEROS.equals(authentication.strip())) {
                problems.add(new ConfigProblem(KDC, ConfigProblem.Reason.INVALID, "KDCs are used only with Kerberos"));
            }
            try {
                KerberosRealms.kdcs(kdc);
            }
            catch (IllegalArgumentException invalid) {
                problems.add(new ConfigProblem(KDC, ConfigProblem.Reason.INVALID, invalid.getMessage()));
            }
            String user = config.get(USER);
            if (user != null) {
                try {
                    KerberosRealms.realm(user.strip());
                }
                catch (IllegalArgumentException invalid) {
                    problems.add(new ConfigProblem(USER, ConfigProblem.Reason.INVALID, invalid.getMessage()));
                }
            }
        }
        try {
            lookupRoot(config);
        }
        catch (IllegalArgumentException invalid) {
            problems.add(new ConfigProblem(LOOKUP_ROOT, ConfigProblem.Reason.INVALID, invalid.getMessage()));
        }
        try {
            scanLimit(config);
        }
        catch (IllegalArgumentException invalid) {
            problems.add(new ConfigProblem(LOOKUP_MAX, ConfigProblem.Reason.INVALID, invalid.getMessage()));
        }
        return problems;
    }

    private static boolean validAddress(String address)
    {
        try {
            URI uri = URI.create(address.strip());
            String scheme = uri.getScheme();
            return scheme != null && SCHEMES.contains(scheme.toLowerCase(Locale.ROOT)) && scheme.equals(scheme.toLowerCase(Locale.ROOT))
                    && !uri.isOpaque() && uri.getUserInfo() == null && uri.getQuery() == null && uri.getFragment() == null
                    && (uri.getPath() == null || uri.getPath().isEmpty() || "/".equals(uri.getPath()))
                    && ("viewfs".equals(scheme) || (uri.getHost() != null && !uri.getHost().isBlank()))
                    && (uri.getPort() == -1 || uri.getPort() > 0 && uri.getPort() <= 65535);
        }
        catch (IllegalArgumentException invalid) {
            return false;
        }
    }

    private static String lookupRoot(ServiceConfig config)
    {
        String root = config.get(LOOKUP_ROOT);
        return normalizePath(root == null || root.isBlank() ? "/" : root.strip());
    }

    private static String normalizePath(String path)
    {
        if (!path.startsWith("/") || path.contains("://") || path.indexOf('\0') >= 0) {
            throw new IllegalArgumentException("use an absolute file system path");
        }
        List<String> components = new ArrayList<>();
        for (String component : path.split("/")) {
            if ("..".equals(component)) {
                throw new IllegalArgumentException("parent path segments (..) are not allowed");
            }
            if (!component.isEmpty() && !".".equals(component)) {
                components.add(component);
            }
        }
        return "/" + String.join("/", components);
    }

    private static int scanLimit(ServiceConfig config)
    {
        long limit = config.getLong(LOOKUP_MAX, 10000);
        if (limit < 1 || limit > 100000) {
            throw new IllegalArgumentException("maximum directory entries must be between 1 and 100000");
        }
        return (int) limit;
    }

    private static boolean blank(@Nullable String value)
    {
        return value == null || value.isBlank();
    }

    @Override
    public ConnectionResult testConnection(ServiceConfig config)
    {
        try {
            String directory = lookupRoot(config);
            return new HadoopClient(config).run(files -> {
                FileStatus root = HdfsDirectoryListing.status(files, new Path(directory));
                if (!root.isDirectory()) {
                    return ConnectionResult.failed("the lookup path is not a directory: " + directory);
                }
                // Metadata access alone does not prove this user can browse paths in the policy editor.
                HdfsDirectoryListing.list(files, new Path(directory), scanLimit(config));
                return ConnectionResult.succeeded();
            });
        }
        catch (IOException | IllegalArgumentException failed) {
            return ConnectionResult.failed(message(failed));
        }
    }

    /**
     * Lists the paths that start with what was typed: the entries of the directory typed so far whose names start with
     * the rest, directories first. A directory typed that does not exist has no entries; the lookup directory itself
     * missing, a refusal, an unreachable cluster, a failed sign-in or a directory over the scan limit fail with the
     * reason named.
     */
    @Override
    public List<String> lookup(LookupRequest request)
    {
        if (!PATH.equals(request.resource())) {
            return List.of();
        }
        String root;
        String directory;
        String prefix;
        int maximum;
        try {
            root = lookupRoot(request.config());
            String typed = request.userInput().strip();
            String path = typed.isEmpty() ? root + "/" : typed.startsWith("/") ? typed : ("/".equals(root) ? "/" : root + "/") + typed;
            boolean children = path.endsWith("/");
            path = normalizePath(path);
            if (!within(path, root)) {
                throw new IllegalArgumentException("lookup path must be inside " + root);
            }
            if (children || path.equals(root)) {
                path = "/".equals(path) ? path : path + "/";
            }
            int slash = path.lastIndexOf('/');
            directory = slash == 0 ? "/" : path.substring(0, slash);
            prefix = path.substring(slash + 1);
            maximum = scanLimit(request.config());
        }
        catch (IllegalArgumentException invalid) {
            throw new LookupException(LookupException.Reason.INVALID_INPUT, String.valueOf(invalid.getMessage()), invalid);
        }
        String base = "/".equals(directory) ? "" : directory;
        List<FileStatus> entries;
        try {
            entries = new HadoopClient(request.config()).run(files -> {
                try {
                    if (!HdfsDirectoryListing.status(files, new Path(directory)).isDirectory()) {
                        return List.of();
                    }
                    return HdfsDirectoryListing.list(files, new Path(directory), maximum);
                }
                catch (FileNotFoundException missing) {
                    return missing(directory, root, missing);
                }
                catch (RemoteException remote) {
                    // An old or unusual NameNode may not name the Java class, so the client cannot unwrap it.
                    if (remote.getClassName() != null && remote.getClassName().endsWith("FileNotFoundException")) {
                        return missing(directory, root, remote);
                    }
                    throw remote;
                }
            });
        }
        catch (IOException failed) {
            throw failure(failed);
        }
        return entries.stream().filter(entry -> entry.getPath().getName().startsWith(prefix))
                .sorted(Comparator.comparing((FileStatus entry) -> !entry.isDirectory()).thenComparing(entry -> entry.getPath().getName()))
                .limit(request.limit()).map(entry -> base + "/" + entry.getPath().getName()).toList();
    }

    /**
     * Lists one page of a directory below the lookup directory, sorted by name, with each entry's owner, group,
     * permissions, size and modification time.
     */
    @Override
    public BrowsePage browse(BrowseRequest request)
    {
        if (!PATH.equals(request.resource())) {
            throw new LookupException(LookupException.Reason.INVALID_INPUT, "only paths can be browsed");
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
        HdfsDirectoryListing.Page page;
        try {
            page = new HadoopClient(request.config()).run(files -> {
                Path path = new Path(directory);
                FileStatus status;
                try {
                    status = HdfsDirectoryListing.status(files, path);
                }
                catch (FileNotFoundException missing) {
                    throw new LookupException(LookupException.Reason.NOT_FOUND, "the directory does not exist: " + directory, missing);
                }
                catch (RemoteException remote) {
                    if (remote.getClassName() != null && remote.getClassName().endsWith("FileNotFoundException")) {
                        throw new LookupException(LookupException.Reason.NOT_FOUND, "the directory does not exist: " + directory, remote);
                    }
                    throw remote;
                }
                if (!status.isDirectory()) {
                    throw new LookupException(LookupException.Reason.INVALID_INPUT, "not a directory: " + directory);
                }
                return HdfsDirectoryListing.page(files, path, request.cursor(), request.pageSize(), maximum);
            });
        }
        catch (IOException failed) {
            throw failure(failed);
        }
        String base = "/".equals(directory) ? "" : directory;
        return new BrowsePage(root, directory, page.entries().stream().map(entry -> entry(entry, base)).toList(), page.next());
    }

    private static BrowseEntry entry(FileStatus status, String base)
    {
        String name = status.getPath().getName();
        return new BrowseEntry(name, base + "/" + name, status.isDirectory(), present(status.getOwner()), present(status.getGroup()),
                status.getPermission() == null ? null : status.getPermission().toString(), status.isDirectory() ? null : status.getLen(),
                status.getModificationTime() > 0 ? Instant.ofEpochMilli(status.getModificationTime()) : null);
    }

    private static @Nullable String present(@Nullable String value)
    {
        return value == null || value.isEmpty() ? null : value;
    }

    /** Whether a normalized path is the root or below it. */
    private static boolean within(String path, String root)
    {
        return "/".equals(root) || path.equals(root) || path.startsWith(root + "/");
    }

    /** A directory that does not exist has no entries, unless it is the lookup directory, which must exist. */
    private static List<FileStatus> missing(String directory, String root, IOException missing)
    {
        if (directory.equals(root)) {
            throw new LookupException(LookupException.Reason.NOT_FOUND, "the lookup directory does not exist: " + root, missing);
        }
        return List.of();
    }

    /** Names why a lookup failed, from the first failure in the chain that tells. */
    static LookupException failure(IOException failed)
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
        if (cause instanceof DirectoryTooLargeException) {
            return LookupException.Reason.LIMIT_EXCEEDED;
        }
        if (cause instanceof HdfsLoginException || cause instanceof SaslException || cause instanceof AuthenticationException
                || cause instanceof LoginException) {
            return LookupException.Reason.AUTHENTICATION_FAILED;
        }
        if (cause instanceof AccessControlException || cause instanceof AccessDeniedException
                || cause instanceof RemoteException remote && remote.getClassName() != null
                && remote.getClassName().endsWith("AccessControlException")) {
            return LookupException.Reason.ACCESS_DENIED;
        }
        // A NameNode whose certificate cannot be verified is as good as unreachable: nothing may be asked of it.
        if (cause instanceof ConnectException || cause instanceof UnknownHostException || cause instanceof NoRouteToHostException
                || cause instanceof SocketTimeoutException || cause instanceof SSLException) {
            return LookupException.Reason.UNREACHABLE;
        }
        return null;
    }

    /** The first line of a Hadoop failure, which is the useful part of its often long message. */
    private static String message(Exception failure)
    {
        String text = failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage();
        int line = text.indexOf('\n');
        return line < 0 ? text : text.substring(0, line);
    }
}

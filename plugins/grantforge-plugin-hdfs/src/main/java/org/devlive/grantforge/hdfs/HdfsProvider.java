// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.hdfs;

import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.ipc.RemoteException;
import org.devlive.grantforge.plugin.api.ConnectionResult;
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

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
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
    static final String DEFAULT_FS = "fs.default.name";
    static final String AUTHORIZATION = "hadoop.security.authorization";
    static final String AUTHENTICATION = "hadoop.security.authentication";
    static final String AUTH_TO_LOCAL = "hadoop.security.auth_to_local";
    static final String DATANODE_PRINCIPAL = "dfs.datanode.kerberos.principal";
    static final String NAMENODE_PRINCIPAL = "dfs.namenode.kerberos.principal";
    static final String SECONDARY_PRINCIPAL = "dfs.secondary.namenode.kerberos.principal";
    static final String RPC_PROTECTION = "hadoop.rpc.protection";
    static final String EXTRA = "hadoop.config";
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
                        .recursiveSupported(true).excludesSupported(true).lookupSupported(true).validLeaf(true).build())
                .accessTypes(AccessTypeDefinition.of("read", "Read"), AccessTypeDefinition.of("write", "Write"),
                        AccessTypeDefinition.of("execute", "Execute"))
                .configFields(
                        ConfigField.builder(USER).label("Username").type(ConfigFieldType.STRING).mandatory()
                                .description("Who lists paths; with Kerberos, the principal, such as grantforge@EXAMPLE.COM").build(),
                        ConfigField.builder(PASSWORD).label("Password").type(ConfigFieldType.SECRET)
                                .description("The principal's Kerberos password, unless a keytab is given").build(),
                        ConfigField.builder(KEYTAB).label("Keytab").type(ConfigFieldType.STRING)
                                .description("Path of the principal's keytab on the GrantForge server").build(),
                        ConfigField.builder(DEFAULT_FS).label("Namenode URL").type(ConfigFieldType.STRING).mandatory()
                                .pattern("[A-Za-z][A-Za-z0-9+.-]*://\\S+")
                                .description("Such as hdfs://namenode:8020, hdfs://nameservice1 or webhdfs://namenode:9870").build(),
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
                        ConfigField.builder(EXTRA).label("Additional Hadoop properties").type(ConfigFieldType.TEXT)
                                .description("One key=value per line, such as the HA nameservice: dfs.nameservices,"
                                        + " dfs.ha.namenodes.<ns>, dfs.namenode.rpc-address.<ns>.<nn>,"
                                        + " dfs.client.failover.proxy.provider.<ns>").build())
                .build();
    }

    @Override
    public List<ConfigProblem> validateConfig(ServiceConfig config)
    {
        List<ConfigProblem> problems = new ArrayList<>();
        String address = config.get(DEFAULT_FS);
        if (address != null) {
            String scheme = scheme(address);
            if (scheme == null || !SCHEMES.contains(scheme)) {
                problems.add(new ConfigProblem(DEFAULT_FS, ConfigProblem.Reason.INVALID, "use hdfs://, webhdfs://, swebhdfs:// or viewfs://"));
            }
        }
        if (KERBEROS.equals(config.get(AUTHENTICATION)) && blank(config.get(PASSWORD)) && blank(config.get(KEYTAB))) {
            problems.add(new ConfigProblem(PASSWORD, ConfigProblem.Reason.REQUIRED, "Kerberos needs a password or a keytab"));
        }
        try {
            HadoopClient.properties(config.get(EXTRA));
        }
        catch (IllegalArgumentException invalid) {
            problems.add(new ConfigProblem(EXTRA, ConfigProblem.Reason.INVALID, invalid.getMessage()));
        }
        return problems;
    }

    private static @Nullable String scheme(String address)
    {
        try {
            String scheme = URI.create(address.strip()).getScheme();
            return scheme == null ? null : scheme.toLowerCase(Locale.ROOT);
        }
        catch (IllegalArgumentException invalid) {
            return null;
        }
    }

    private static boolean blank(@Nullable String value)
    {
        return value == null || value.isBlank();
    }

    @Override
    public ConnectionResult testConnection(ServiceConfig config)
    {
        try {
            FileStatus root = new HadoopClient(config).run(files -> files.getFileStatus(new Path("/")));
            return root.isDirectory() ? ConnectionResult.succeeded() : ConnectionResult.failed("the root of the file system is not a directory");
        }
        catch (IOException | IllegalArgumentException failed) {
            return ConnectionResult.failed(message(failed));
        }
    }

    /**
     * Lists the paths that start with what was typed: the entries of the directory typed so far whose names start with
     * the rest, directories first.
     */
    @Override
    public List<String> lookup(LookupRequest request)
    {
        if (!PATH.equals(request.resource())) {
            return List.of();
        }
        String typed = request.userInput().strip();
        String path = typed.startsWith("/") ? typed : "/" + typed;
        int slash = path.lastIndexOf('/');
        String directory = slash == 0 ? "/" : path.substring(0, slash);
        String prefix = path.substring(slash + 1);
        String base = "/".equals(directory) ? "" : directory;
        List<FileStatus> entries;
        try {
            entries = new HadoopClient(request.config()).run(files -> {
                try {
                    return Arrays.asList(files.listStatus(new Path(directory)));
                }
                catch (FileNotFoundException missing) {
                    return List.of();
                }
                catch (RemoteException remote) {
                    // An old or unusual NameNode may not name the Java class, so the client cannot unwrap it.
                    if (remote.getClassName() != null && remote.getClassName().endsWith("FileNotFoundException")) {
                        return List.of();
                    }
                    throw remote;
                }
            });
        }
        catch (IOException failed) {
            throw new UncheckedIOException(message(failed), failed);
        }
        return entries.stream().filter(entry -> entry.getPath().getName().startsWith(prefix))
                .sorted(Comparator.comparing((FileStatus entry) -> !entry.isDirectory()).thenComparing(entry -> entry.getPath().getName()))
                .limit(request.limit()).map(entry -> base + "/" + entry.getPath().getName()).toList();
    }

    /** The first line of a Hadoop failure, which is the useful part of its often long message. */
    private static String message(Exception failure)
    {
        String text = failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage();
        int line = text.indexOf('\n');
        return line < 0 ? text : text.substring(0, line);
    }
}

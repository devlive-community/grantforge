// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.hive;

import org.devlive.grantforge.plugin.api.ConnectionResult;
import org.devlive.grantforge.plugin.api.KerberosRealms;
import org.devlive.grantforge.plugin.api.LookupException;
import org.devlive.grantforge.plugin.api.LookupRequest;
import org.devlive.grantforge.plugin.api.ServiceConfig;
import org.devlive.grantforge.plugin.api.ServiceTypeProvider;
import org.devlive.grantforge.plugin.api.model.AccessTypeDefinition;
import org.devlive.grantforge.plugin.api.model.ConfigField;
import org.devlive.grantforge.plugin.api.model.ConfigFieldType;
import org.devlive.grantforge.plugin.api.model.ConfigProblem;
import org.devlive.grantforge.plugin.api.model.DataMaskDefinition;
import org.devlive.grantforge.plugin.api.model.MaskTypeDefinition;
import org.devlive.grantforge.plugin.api.model.MatcherType;
import org.devlive.grantforge.plugin.api.model.ResourceDefinition;
import org.devlive.grantforge.plugin.api.model.RowFilterDefinition;
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
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * The Apache Hive service type, as Apache Ranger's: databases with their tables, columns and UDFs, URLs of the data
 * Hive reads and writes, the HiveServer2 service itself and the global scope of replication, with Hive's access types.
 * Masks are Hive's own masking UDFs, in which {@code {col}} is the column; row filters are Hive conditions on a table.
 * Lookups go through HiveServer2's JDBC driver, with a password or Kerberos.
 */
public final class HiveProvider
        implements ServiceTypeProvider
{
    /** The service type's name. */
    public static final String TYPE = "hive";

    static final String DATABASE = "database";
    static final String TABLE = "table";
    static final String COLUMN = "column";
    static final String UDF = "udf";
    static final String URL_RESOURCE = "url";
    static final String SERVICE = "hiveservice";
    static final String GLOBAL = "global";
    static final String URL = "jdbc.url";
    static final String AUTHENTICATION = "authentication";
    static final String USER = "username";
    static final String PASSWORD = "password";
    static final String KEYTAB = "keytab";
    static final String KDC = "kerberos.kdc";
    static final String READ_TIMEOUT = "read.timeout.ms";
    static final String PASSWORD_AUTH = "password";
    static final String KERBEROS = "kerberos";

    private static final Set<String> AUTHENTICATIONS = Set.of(PASSWORD_AUTH, KERBEROS);

    @Override
    public ServiceTypeDefinition definition()
    {
        return ServiceTypeDefinition.builder(TYPE).label("Apache Hive").description("Databases, tables, columns and UDFs of HiveServer2")
                .resources(level(DATABASE, "Database", null).validLeaf(true).build(),
                        level(TABLE, "Table", DATABASE).validLeaf(true).build(),
                        level(UDF, "UDF", DATABASE).build(),
                        level(COLUMN, "Column", TABLE).build(),
                        ResourceDefinition.builder(URL_RESOURCE).label("URL").matcher(MatcherType.PATH).caseSensitive(true)
                                .recursiveSupported(true).excludesSupported(false).accessTypes("read", "write").build(),
                        ResourceDefinition.builder(SERVICE).label("HiveServer2").matcher(MatcherType.WILDCARD).caseSensitive(false)
                                .excludesSupported(false).accessTypes("serviceadmin").build(),
                        ResourceDefinition.builder(GLOBAL).label("Global").matcher(MatcherType.WILDCARD).caseSensitive(false)
                                .excludesSupported(false).accessTypes("repladmin").build())
                .accessTypes(AccessTypeDefinition.of("select", "Select"), AccessTypeDefinition.of("update", "Update"),
                        AccessTypeDefinition.of("create", "Create"), AccessTypeDefinition.of("drop", "Drop"),
                        AccessTypeDefinition.of("alter", "Alter"), AccessTypeDefinition.of("index", "Index"),
                        AccessTypeDefinition.of("lock", "Lock"), AccessTypeDefinition.of("read", "Read"),
                        AccessTypeDefinition.of("write", "Write"), AccessTypeDefinition.of("repladmin", "Replication admin"),
                        AccessTypeDefinition.of("serviceadmin", "Service admin"), AccessTypeDefinition.of("tempudfadmin", "Temporary UDF admin"),
                        AccessTypeDefinition.of("refresh", "Refresh"),
                        AccessTypeDefinition.of("all", "All", "select", "update", "create", "drop", "alter", "index", "lock", "read", "write",
                                "repladmin", "serviceadmin", "tempudfadmin", "refresh"))
                .dataMask(new DataMaskDefinition(Set.of(COLUMN), List.of(
                        new MaskTypeDefinition("mask", "Redact", "mask({col})"),
                        new MaskTypeDefinition("mask_show_last_4", "Partial mask: show last 4", "mask_show_last_n({col}, 4, 'x', 'x', 'x', -1, '1')"),
                        new MaskTypeDefinition("mask_show_first_4", "Partial mask: show first 4", "mask_show_first_n({col}, 4, 'x', 'x', 'x', -1, '1')"),
                        new MaskTypeDefinition("mask_hash", "Hash", "mask_hash({col})"),
                        new MaskTypeDefinition("mask_null", "Nullify", "NULL"),
                        new MaskTypeDefinition("mask_none", "Unmasked (retain original value)", "{col}"),
                        new MaskTypeDefinition("mask_date_show_year", "Date: show only year", "mask({col}, 'x', 'x', 'x', -1, '1', 1, 0, -1)"),
                        new MaskTypeDefinition("custom", "Custom", "{expr}"))))
                .rowFilter(new RowFilterDefinition(Set.of(TABLE)))
                .configFields(
                        ConfigField.builder(URL).label("JDBC URL").type(ConfigFieldType.STRING).mandatory().pattern("jdbc:hive2://\\S+")
                                .description("jdbc:hive2://hiveserver2:10000/default; ZooKeeper discovery such as jdbc:hive2://zk1:2181,zk2:2181/"
                                        + ";serviceDiscoveryMode=zooKeeper;zooKeeperNamespace=hiveserver2; TLS with ;ssl=true;sslTrustStore=…;"
                                        + " Kerberos with ;principal=hive/_HOST@EXAMPLE.COM").build(),
                        ConfigField.builder(AUTHENTICATION).label("Authentication").type(ConfigFieldType.ENUM).options(PASSWORD_AUTH, KERBEROS)
                                .defaultValue(PASSWORD_AUTH).description("password: a user with its password (LDAP, PAM or none); kerberos: a"
                                        + " principal with a keytab or password").build(),
                        ConfigField.builder(USER).label("Username").type(ConfigFieldType.STRING).mandatory()
                                .description("Who looks up databases and tables; with Kerberos, the principal, such as grantforge@EXAMPLE.COM").build(),
                        ConfigField.builder(PASSWORD).label("Password").type(ConfigFieldType.SECRET)
                                .description("The user's password, or the principal's Kerberos password unless a keytab is given").build(),
                        ConfigField.builder(KEYTAB).label("Keytab").type(ConfigFieldType.STRING)
                                .description("Kerberos: path of the principal's keytab on the GrantForge server").build(),
                        ConfigField.builder(KDC).label("KDCs").type(ConfigFieldType.STRING)
                                .description("Kerberos: KDCs of the principal's realm, host[:port] separated by commas; empty uses the"
                                        + " server's krb5.conf").build(),
                        ConfigField.builder(READ_TIMEOUT).label("Read timeout (ms)").type(ConfigFieldType.INTEGER).defaultValue("30000")
                                .description("Between 1 and 600000").build())
                .build();
    }

    private static ResourceDefinition.Builder level(String name, String label, @Nullable String parent)
    {
        ResourceDefinition.Builder level = ResourceDefinition.builder(name).label(label).matcher(MatcherType.WILDCARD).caseSensitive(false)
                .excludesSupported(true).lookupSupported(true);
        return parent == null ? level : level.parent(parent);
    }

    @Override
    public List<ConfigProblem> validateConfig(ServiceConfig config)
    {
        List<ConfigProblem> problems = new ArrayList<>();
        String authentication = config.get(AUTHENTICATION);
        String mode = authentication == null || authentication.isBlank() ? PASSWORD_AUTH : authentication.strip().toLowerCase(Locale.ROOT);
        if (!AUTHENTICATIONS.contains(mode)) {
            problems.add(new ConfigProblem(AUTHENTICATION, ConfigProblem.Reason.INVALID, "authentication must be password or kerberos"));
        }
        boolean kerberos = KERBEROS.equals(mode);
        String url = config.get(URL);
        if (url != null && !url.isBlank()) {
            try {
                HiveUrl.check(url, kerberos);
            }
            catch (IllegalArgumentException invalid) {
                problems.add(new ConfigProblem(URL, ConfigProblem.Reason.INVALID, invalid.getMessage()));
            }
        }
        if (kerberos) {
            kerberos(config, problems);
        }
        else {
            for (String field : List.of(KEYTAB, KDC)) {
                if (!blank(config.get(field))) {
                    problems.add(new ConfigProblem(field, ConfigProblem.Reason.INVALID, "only Kerberos uses a keytab and KDCs"));
                }
            }
        }
        try {
            HiveClient.timeout(config);
        }
        catch (IllegalArgumentException invalid) {
            problems.add(new ConfigProblem(READ_TIMEOUT, ConfigProblem.Reason.INVALID, invalid.getMessage()));
        }
        return problems;
    }

    private static void kerberos(ServiceConfig config, List<ConfigProblem> problems)
    {
        String user = config.get(USER);
        if (user != null && !user.isBlank()) {
            try {
                KerberosRealms.realm(user.strip());
            }
            catch (IllegalArgumentException invalid) {
                problems.add(new ConfigProblem(USER, ConfigProblem.Reason.INVALID, invalid.getMessage()));
            }
        }
        if (blank(config.get(PASSWORD)) && blank(config.get(KEYTAB))) {
            problems.add(new ConfigProblem(PASSWORD, ConfigProblem.Reason.REQUIRED, "Kerberos needs a password or a keytab"));
        }
        String kdc = config.get(KDC);
        if (kdc != null && !kdc.isBlank()) {
            try {
                KerberosRealms.kdcs(kdc);
            }
            catch (IllegalArgumentException invalid) {
                problems.add(new ConfigProblem(KDC, ConfigProblem.Reason.INVALID, invalid.getMessage()));
            }
        }
    }

    private static boolean blank(@Nullable String value)
    {
        return value == null || value.isBlank();
    }

    /** Signs in and reads the databases, as lookups do. */
    @Override
    public ConnectionResult testConnection(ServiceConfig config)
    {
        try {
            return new HiveClient(config).run(connection -> {
                DatabaseMetaData metadata = connection.getMetaData();
                try (ResultSet databases = metadata.getSchemas()) {
                    databases.next();
                }
                return ConnectionResult.succeeded();
            });
        }
        catch (SQLException | LoginException | IOException | IllegalArgumentException failed) {
            return ConnectionResult.failed(message(failed));
        }
    }

    /**
     * Lists the databases, tables, columns or UDFs whose names start with what was typed, within the databases and
     * tables the policy names already. URLs, the service and the global scope have nothing to list.
     */
    @Override
    public List<String> lookup(LookupRequest request)
    {
        if (!Set.of(DATABASE, TABLE, COLUMN, UDF).contains(request.resource())) {
            return List.of();
        }
        HiveLookup lookup = HiveLookup.of(request.resource(), request.userInput(), request.context(), request.limit());
        HiveClient client;
        try {
            client = new HiveClient(request.config());
        }
        catch (IllegalArgumentException invalid) {
            throw new LookupException(LookupException.Reason.INVALID_INPUT, String.valueOf(invalid.getMessage()), invalid);
        }
        try {
            return client.run(lookup::run);
        }
        catch (SQLException | LoginException | IOException failed) {
            throw failure(failed);
        }
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
        String text = String.valueOf(cause.getMessage());
        // HiveServer2 refuses a password over SASL; the driver reports it as a transport failure that names it.
        if (cause instanceof LoginException || cause instanceof SaslException || text.contains("Error validating the login")
                || text.contains("Peer indicated failure")) {
            return LookupException.Reason.AUTHENTICATION_FAILED;
        }
        if (text.contains("Permission denied") || cause instanceof SQLException sql && "42000".equals(sql.getSQLState())) {
            return LookupException.Reason.ACCESS_DENIED;
        }
        // A server whose certificate cannot be verified is as good as unreachable: nothing may be asked of it.
        if (cause instanceof ConnectException || cause instanceof UnknownHostException || cause instanceof NoRouteToHostException
                || cause instanceof SocketTimeoutException || cause instanceof SSLException) {
            return LookupException.Reason.UNREACHABLE;
        }
        return null;
    }

    /** The first line of a failure, which is the useful part of the driver's often long message. */
    static String message(Exception failure)
    {
        String text = failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage();
        int line = text.indexOf('\n');
        return line < 0 ? text : text.substring(0, line);
    }
}

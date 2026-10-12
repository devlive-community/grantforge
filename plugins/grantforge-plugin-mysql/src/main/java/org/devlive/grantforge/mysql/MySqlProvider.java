// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.mysql;

import org.devlive.grantforge.plugin.api.ConnectionResult;
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

import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLInvalidAuthorizationSpecException;
import java.sql.SQLNonTransientConnectionException;
import java.sql.SQLTimeoutException;
import java.sql.SQLTransientConnectionException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The MySQL service type: databases, their tables and the tables' columns, with MySQL's own privileges, masking of
 * columns and row filters of tables. Databases and tables compare case-sensitively, as MySQL stores them on Linux; an
 * agent of a server with {@code lower_case_table_names} set folds the names it is asked about. Columns compare without
 * case, as MySQL compares them. Masks and row filters are MySQL 8 expressions in which {@code {col}} is the column.
 * Lookups read information_schema through MariaDB Connector/J.
 */
public final class MySqlProvider
        implements ServiceTypeProvider
{
    /** The service type's name. */
    public static final String TYPE = "mysql";

    static final String DATABASE = "database";
    static final String TABLE = "table";
    static final String COLUMN = "column";
    static final String URL = "jdbc.url";
    static final String USER = "username";
    static final String PASSWORD = "password";
    static final String PROPERTIES = "jdbc.properties";
    static final String CONNECT_TIMEOUT = "connect.timeout.ms";
    static final String READ_TIMEOUT = "read.timeout.ms";

    /** The oldest server whose expressions the masks use (REGEXP_REPLACE with a match type). */
    static final int MINIMUM_MAJOR = 8;

    private static final Pattern VERSION = Pattern.compile("(\\d+)\\.(\\d+)");

    @Override
    public ServiceTypeDefinition definition()
    {
        return ServiceTypeDefinition.builder(TYPE).label("MySQL").description("Databases, tables and columns of MySQL servers")
                .resources(ResourceDefinition.builder(DATABASE).label("Database").matcher(MatcherType.WILDCARD).caseSensitive(true)
                                .excludesSupported(true).lookupSupported(true).validLeaf(true).build(),
                        ResourceDefinition.builder(TABLE).label("Table").parent(DATABASE).matcher(MatcherType.WILDCARD).caseSensitive(true)
                                .excludesSupported(true).lookupSupported(true).validLeaf(true).build(),
                        ResourceDefinition.builder(COLUMN).label("Column").parent(TABLE).matcher(MatcherType.WILDCARD).caseSensitive(false)
                                .excludesSupported(true).lookupSupported(true).accessTypes("select", "insert", "update").build())
                .accessTypes(AccessTypeDefinition.of("select", "Select"), AccessTypeDefinition.of("insert", "Insert"),
                        AccessTypeDefinition.of("update", "Update"), AccessTypeDefinition.of("delete", "Delete"),
                        AccessTypeDefinition.of("create", "Create"), AccessTypeDefinition.of("drop", "Drop"),
                        AccessTypeDefinition.of("alter", "Alter"), AccessTypeDefinition.of("index", "Index"),
                        AccessTypeDefinition.of("all", "All", "select", "insert", "update", "delete", "create", "drop", "alter", "index"))
                .dataMask(new DataMaskDefinition(Set.of(COLUMN), List.of(
                        new MaskTypeDefinition("mask", "Redact", "REGEXP_REPLACE(REGEXP_REPLACE(REGEXP_REPLACE({col}, '[A-Z]', 'X', 1, 0, 'c'),"
                                + " '[a-z]', 'x', 1, 0, 'c'), '[0-9]', 'n', 1, 0, 'c')"),
                        new MaskTypeDefinition("mask_show_last_4", "Partial mask: show last 4",
                                "CONCAT(REPEAT('x', GREATEST(CHAR_LENGTH({col}) - 4, 0)), RIGHT({col}, 4))"),
                        new MaskTypeDefinition("mask_show_first_4", "Partial mask: show first 4",
                                "CONCAT(LEFT({col}, 4), REPEAT('x', GREATEST(CHAR_LENGTH({col}) - 4, 0)))"),
                        new MaskTypeDefinition("mask_hash", "Hash", "SHA2({col}, 256)"),
                        new MaskTypeDefinition("mask_null", "Nullify", "NULL"),
                        new MaskTypeDefinition("mask_none", "Unmasked (retain original value)", "{col}"),
                        new MaskTypeDefinition("mask_date_show_year", "Date: show only year", "MAKEDATE(YEAR({col}), 1)"),
                        new MaskTypeDefinition("custom", "Custom", "{expr}"))))
                .rowFilter(new RowFilterDefinition(Set.of(TABLE)))
                .configFields(
                        ConfigField.builder(URL).label("JDBC URL").type(ConfigFieldType.STRING).mandatory()
                                .pattern("jdbc:(mysql|mariadb):\\S+")
                                .description("jdbc:mysql://mysql.example.com:3306, or a failover form such as"
                                        + " jdbc:mysql:replication://primary:3306,replica:3306; options may follow after ?").build(),
                        ConfigField.builder(USER).label("Username").type(ConfigFieldType.STRING).mandatory()
                                .description("Who looks up databases, tables and columns; reading information_schema is enough").build(),
                        ConfigField.builder(PASSWORD).label("Password").type(ConfigFieldType.SECRET).build(),
                        ConfigField.builder(PROPERTIES).label("Driver options").type(ConfigFieldType.TEXT)
                                .description("One key=value per line for MariaDB Connector/J, such as sslMode=verify-full and"
                                        + " serverSslCert=/etc/grantforge/mysql-ca.pem").build(),
                        ConfigField.builder(CONNECT_TIMEOUT).label("Connect timeout (ms)").type(ConfigFieldType.INTEGER)
                                .defaultValue("10000").description("Between 1 and 600000").build(),
                        ConfigField.builder(READ_TIMEOUT).label("Read timeout (ms)").type(ConfigFieldType.INTEGER)
                                .defaultValue("30000").description("Between 1 and 600000").build())
                .build();
    }

    @Override
    public List<ConfigProblem> validateConfig(ServiceConfig config)
    {
        List<ConfigProblem> problems = new ArrayList<>();
        String url = config.get(URL);
        if (url != null && !url.isBlank()) {
            try {
                MySqlClient.driverUrl(url);
            }
            catch (IllegalArgumentException invalid) {
                problems.add(new ConfigProblem(URL, ConfigProblem.Reason.INVALID, invalid.getMessage()));
            }
        }
        try {
            MySqlClient.options(config.get(PROPERTIES));
        }
        catch (IllegalArgumentException invalid) {
            problems.add(new ConfigProblem(PROPERTIES, ConfigProblem.Reason.INVALID, invalid.getMessage()));
        }
        for (String field : List.of(CONNECT_TIMEOUT, READ_TIMEOUT)) {
            try {
                MySqlClient.timeout(config, field, 1);
            }
            catch (IllegalArgumentException invalid) {
                problems.add(new ConfigProblem(field, ConfigProblem.Reason.INVALID, invalid.getMessage()));
            }
        }
        return problems;
    }

    /**
     * Signs in, checks that the server is MySQL 8.0 or later, whose expressions the masks use, and reads
     * information_schema as lookups do.
     */
    @Override
    public ConnectionResult testConnection(ServiceConfig config)
    {
        try {
            return new MySqlClient(config).run(connection -> {
                String version;
                try (Statement statement = connection.createStatement(); ResultSet row = statement.executeQuery("SELECT VERSION()")) {
                    version = row.next() ? row.getString(1) : "";
                }
                String unsupported = unsupported(version);
                if (unsupported != null) {
                    return ConnectionResult.failed(unsupported);
                }
                MySqlLookup.of(DATABASE, "", Map.of(), 1).run(connection);
                return ConnectionResult.succeeded();
            });
        }
        catch (SQLException | IllegalArgumentException failed) {
            return ConnectionResult.failed(message(failed));
        }
    }

    /**
     * Says why a server cannot be a MySQL service.
     *
     * @param version what {@code VERSION()} returned
     * @return the reason, or {@code null} for MySQL 8.0 and later
     */
    static @Nullable String unsupported(String version)
    {
        if (version.toLowerCase(Locale.ROOT).contains("mariadb")) {
            return "the server is MariaDB " + version + ", not MySQL; its masking expressions differ";
        }
        Matcher matcher = VERSION.matcher(version);
        if (!matcher.lookingAt()) {
            return "the server's version cannot be read: " + version;
        }
        if (Integer.parseInt(matcher.group(1)) < MINIMUM_MAJOR) {
            return "the server is MySQL " + version + "; GrantForge needs MySQL 8.0 or later";
        }
        return null;
    }

    /**
     * Lists the databases, tables or columns whose names start with what was typed, within the databases and tables the
     * policy names already.
     */
    @Override
    public List<String> lookup(LookupRequest request)
    {
        MySqlLookup query;
        MySqlClient client;
        try {
            query = MySqlLookup.of(request.resource(), request.userInput(), request.context(), request.limit());
            client = new MySqlClient(request.config());
        }
        catch (IllegalArgumentException invalid) {
            throw new LookupException(LookupException.Reason.INVALID_INPUT, String.valueOf(invalid.getMessage()), invalid);
        }
        try {
            return client.run(query::run);
        }
        catch (SQLException failed) {
            throw failure(failed);
        }
    }

    /** Names why a lookup failed, from the first failure in the chain that tells. */
    static LookupException failure(SQLException failed)
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
        if (cause instanceof SQLException sql) {
            String state = sql.getSQLState() == null ? "" : sql.getSQLState();
            int code = sql.getErrorCode();
            // 1045: access denied for user (bad password); 1698: no password for a socket-authenticated user.
            if (sql instanceof SQLInvalidAuthorizationSpecException || code == 1045 || code == 1698 || state.startsWith("28")) {
                return LookupException.Reason.AUTHENTICATION_FAILED;
            }
            // 1044: no access to a database; 1142, 1143: no table or column privilege; 1227: missing global privilege.
            if (code == 1044 || code == 1142 || code == 1143 || code == 1227) {
                return LookupException.Reason.ACCESS_DENIED;
            }
            if (sql instanceof SQLTimeoutException || sql instanceof SQLNonTransientConnectionException
                    || sql instanceof SQLTransientConnectionException || state.startsWith("08")) {
                return LookupException.Reason.UNREACHABLE;
            }
        }
        // A server whose certificate cannot be verified is as good as unreachable: nothing may be asked of it.
        if (cause instanceof ConnectException || cause instanceof UnknownHostException || cause instanceof NoRouteToHostException
                || cause instanceof SocketTimeoutException || cause instanceof SSLException) {
            return LookupException.Reason.UNREACHABLE;
        }
        return null;
    }

    /** The first line of a failure, which is the useful part of the driver's message. */
    private static String message(Exception failure)
    {
        String text = failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage();
        int line = text.indexOf('\n');
        return line < 0 ? text : text.substring(0, line);
    }
}

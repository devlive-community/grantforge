// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.trino;

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

import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

/**
 * The Trino service type in Trino's own terms. Data lives in catalogs, schemas, tables and columns, and a permission on a
 * scope covers what is within it; routines are a schema's functions and procedures, granted by naming them, with access
 * types of their own so that a grant on routines never reads as one on tables; catalogs have session properties of
 * their own beside the system's; users may be impersonated and their queries viewed or killed; and system information
 * may be read or written. Row filters and column masks are Trino SQL, which the coordinator applies to every query.
 */
public final class TrinoProvider
        implements ServiceTypeProvider
{
    static final String TYPE = "trino";
    static final String CATALOG = "catalog";
    static final String SCHEMA = "schema";
    static final String TABLE = "table";
    static final String COLUMN = "column";
    static final String FUNCTION = "function";
    static final String PROCEDURE = "procedure";
    static final String SESSION_PROPERTY = "session_property";
    static final String SYSTEM_SESSION_PROPERTY = "system_session_property";
    static final String USER = "user";
    static final String SYSTEM_INFORMATION = "system_information";

    static final String SELECT = "select";
    static final String INSERT = "insert";
    static final String UPDATE = "update";
    static final String DELETE = "delete";
    static final String CREATE = "create";
    static final String DROP = "drop";
    static final String ALTER = "alter";
    static final String EXECUTE = "execute";
    static final String CREATE_FUNCTION = "create_function";
    static final String DROP_FUNCTION = "drop_function";
    static final String SET = "set";
    static final String IMPERSONATE = "impersonate";
    static final String VIEW_QUERY = "view_query";
    static final String KILL_QUERY = "kill_query";
    static final String READ = "read";
    static final String WRITE = "write";
    static final String ALL = "all";

    /** The one value of the system information level. */
    static final String SYSTEM_VALUE = "*";

    private static final String[] DATA = {SELECT, INSERT, UPDATE, DELETE, CREATE, DROP, ALTER, ALL};

    private final Function<TrinoSettings, TrinoClient> clients;

    /** Creates the provider the plugin host loads. */
    public TrinoProvider()
    {
        this(TrinoClient::new);
    }

    TrinoProvider(Function<TrinoSettings, TrinoClient> clients)
    {
        this.clients = clients;
    }

    @Override
    public ServiceTypeDefinition definition()
    {
        return ServiceTypeDefinition.builder(TYPE).label("Trino").description("Catalogs, schemas, tables and columns of Trino, and its access checks")
                .resources(level(CATALOG, "Catalog", null).validLeaf(true).accessTypes(DATA).build(),
                        level(SCHEMA, "Schema", CATALOG).validLeaf(true).accessTypes(DATA).build(),
                        level(TABLE, "Table", SCHEMA).validLeaf(true).accessTypes(DATA).build(),
                        level(COLUMN, "Column", TABLE).accessTypes(SELECT, UPDATE).build(),
                        level(FUNCTION, "Function", SCHEMA).lookupSupported(false).accessTypes(EXECUTE, CREATE_FUNCTION, DROP_FUNCTION).build(),
                        level(PROCEDURE, "Procedure", SCHEMA).lookupSupported(false).accessTypes(EXECUTE).build(),
                        level(SESSION_PROPERTY, "Session property", CATALOG).accessTypes(SET).build(),
                        level(SYSTEM_SESSION_PROPERTY, "System session property", null).accessTypes(SET).build(),
                        ResourceDefinition.builder(USER).label("User").matcher(MatcherType.WILDCARD).caseSensitive(true).excludesSupported(true)
                                .lookupSupported(false).accessTypes(IMPERSONATE, VIEW_QUERY, KILL_QUERY).build(),
                        ResourceDefinition.builder(SYSTEM_INFORMATION).label("System information").matcher(MatcherType.WILDCARD).caseSensitive(true)
                                .excludesSupported(false).lookupSupported(true).accessTypes(READ, WRITE).build())
                .accessTypes(AccessTypeDefinition.of(SELECT, "Select"), AccessTypeDefinition.of(INSERT, "Insert"), AccessTypeDefinition.of(UPDATE, "Update"),
                        AccessTypeDefinition.of(DELETE, "Delete"), AccessTypeDefinition.of(CREATE, "Create"), AccessTypeDefinition.of(DROP, "Drop"),
                        AccessTypeDefinition.of(ALTER, "Alter"), AccessTypeDefinition.of(EXECUTE, "Execute"),
                        AccessTypeDefinition.of(CREATE_FUNCTION, "Create functions"), AccessTypeDefinition.of(DROP_FUNCTION, "Drop functions"), AccessTypeDefinition.of(SET, "Set"),
                        AccessTypeDefinition.of(IMPERSONATE, "Impersonate"), AccessTypeDefinition.of(VIEW_QUERY, "View queries"),
                        AccessTypeDefinition.of(KILL_QUERY, "Kill queries"), AccessTypeDefinition.of(READ, "Read"), AccessTypeDefinition.of(WRITE, "Write"),
                        AccessTypeDefinition.of(ALL, "All", SELECT, INSERT, UPDATE, DELETE, CREATE, DROP, ALTER))
                .dataMask(new DataMaskDefinition(Set.of(COLUMN), List.of(
                        new MaskTypeDefinition("mask", "Redact",
                                "regexp_replace(regexp_replace(regexp_replace({col}, '[A-Z]', 'X'), '[a-z]', 'x'), '[0-9]', 'n')"),
                        new MaskTypeDefinition("mask_show_last_4", "Partial mask: show last 4",
                                "if(length({col}) <= 4, {col}, concat(regexp_replace(substr({col}, 1, length({col}) - 4), '.', 'x'), substr({col}, -4)))"),
                        new MaskTypeDefinition("mask_show_first_4", "Partial mask: show first 4",
                                "if(length({col}) <= 4, {col}, concat(substr({col}, 1, 4), regexp_replace(substr({col}, 5), '.', 'x')))"),
                        new MaskTypeDefinition("mask_hash", "Hash", "to_hex(sha256(to_utf8({col})))"),
                        new MaskTypeDefinition("mask_null", "Nullify", "NULL"),
                        new MaskTypeDefinition("mask_none", "Unmasked (retain original value)", "{col}"),
                        new MaskTypeDefinition("mask_date_show_year", "Date: show only year", "date_trunc('year', {col})"),
                        new MaskTypeDefinition("custom", "Custom", "{expr}"))))
                .rowFilter(new RowFilterDefinition(Set.of(TABLE)))
                .configFields(
                        ConfigField.builder(TrinoSettings.URL).label("JDBC URL").type(ConfigFieldType.STRING).mandatory().pattern("jdbc:trino://\\S+")
                                .description("jdbc:trino://coordinator:8443, with any driver property but credentials, such as ?SSL=true").build(),
                        ConfigField.builder(TrinoSettings.AUTHENTICATION).label("Authentication").type(ConfigFieldType.ENUM)
                                .options(TrinoSettings.AUTHENTICATIONS.toArray(new String[0])).defaultValue(TrinoSettings.NONE)
                                .description("none: the user alone; password; jwt: a JSON web token; kerberos: a principal with a keytab or password")
                                .build(),
                        ConfigField.builder(TrinoSettings.USER).label("User").type(ConfigFieldType.STRING).mandatory()
                                .description("Who looks up catalogs and tables; with Kerberos the principal, such as grantforge@EXAMPLE.COM").build(),
                        ConfigField.builder(TrinoSettings.PASSWORD).label("Password").type(ConfigFieldType.SECRET)
                                .description("The user's password, or the principal's Kerberos password unless a keytab is given").build(),
                        ConfigField.builder(TrinoSettings.ACCESS_TOKEN).label("Access token").type(ConfigFieldType.SECRET)
                                .description("jwt: the JSON web token the coordinator accepts").build(),
                        ConfigField.builder(TrinoSettings.KEYTAB).label("Keytab").type(ConfigFieldType.STRING)
                                .description("Kerberos: path of the principal's keytab on the GrantForge server").build(),
                        ConfigField.builder(TrinoSettings.KDC).label("KDCs").type(ConfigFieldType.STRING)
                                .description("Kerberos: KDCs of the principal's realm, host[:port] separated by commas; empty uses the server's"
                                        + " krb5.conf").build(),
                        ConfigField.builder(TrinoSettings.SERVICE_NAME).label("Kerberos service name").type(ConfigFieldType.STRING).defaultValue("trino")
                                .description("Kerberos: the first part of the coordinator's principal").build(),
                        ConfigField.builder(TrinoSettings.CANONICAL_HOST).label("Canonical host name").type(ConfigFieldType.BOOLEAN)
                                .defaultValue("true").description("Kerberos: whether the coordinator's principal takes the canonical name of"
                                        + " the URL's host; turn off behind a load balancer or alias whose name the principal carries").build(),
                        ConfigField.builder(TrinoSettings.SSL).label("TLS").type(ConfigFieldType.BOOLEAN).defaultValue("false")
                                .description("Whether to speak TLS to the coordinator; signing in other than none needs it").build(),
                        ConfigField.builder(TrinoSettings.TRUSTSTORE).label("Truststore").type(ConfigFieldType.STRING)
                                .description("TLS: path of the truststore that verifies the coordinator; empty trusts what Java trusts").build(),
                        ConfigField.builder(TrinoSettings.TRUSTSTORE_PASSWORD).label("Truststore password").type(ConfigFieldType.SECRET).build(),
                        ConfigField.builder(TrinoSettings.TRUSTSTORE_TYPE).label("Truststore type").type(ConfigFieldType.ENUM)
                                .options(TrinoSettings.STORE_TYPES.toArray(new String[0])).defaultValue("JKS").build(),
                        ConfigField.builder(TrinoSettings.VERIFICATION).label("Certificate verification").type(ConfigFieldType.ENUM)
                                .options(TrinoSettings.VERIFICATIONS.toArray(new String[0])).defaultValue("FULL")
                                .description("TLS: FULL checks the certificate and host name, CA the certificate alone, NONE nothing and takes no truststore")
                                .build(),
                        ConfigField.builder(TrinoSettings.TIMEOUT).label("Timeout (ms)").type(ConfigFieldType.INTEGER)
                                .defaultValue(Integer.toString(TrinoSettings.DEFAULT_TIMEOUT)).description("Between 1 and 600000").build())
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
        return TrinoSettings.problems(config);
    }

    /** Signs in and describes the coordinator: its release and how many catalogs the user sees. */
    @Override
    public ConnectionResult testConnection(ServiceConfig config)
    {
        try {
            return clients.apply(TrinoSettings.of(config)).run(connection -> {
                DatabaseMetaData metadata = connection.getMetaData();
                int catalogs = 0;
                try (ResultSet rows = metadata.getCatalogs()) {
                    while (rows.next()) {
                        catalogs++;
                    }
                }
                return new ConnectionResult(ConnectionResult.Status.SUCCEEDED, "Trino " + metadata.getDatabaseProductVersion() + ", " + catalogs
                        + (catalogs == 1 ? " catalog" : " catalogs"));
            });
        }
        catch (LookupException failed) {
            return ConnectionResult.failed(String.valueOf(failed.getMessage()));
        }
        catch (IllegalArgumentException invalid) {
            return ConnectionResult.failed(String.valueOf(invalid.getMessage()));
        }
    }

    /**
     * Lists the catalogs, schemas, tables, columns or session properties whose names start with what was typed, within
     * the catalogs, schemas and tables the policy names already. System information has its one value; users and
     * routines are not listed.
     */
    @Override
    public List<String> lookup(LookupRequest request)
    {
        TrinoSettings settings;
        TrinoLookup lookup;
        try {
            settings = TrinoSettings.of(request.config());
            if (SYSTEM_INFORMATION.equals(request.resource())) {
                return SYSTEM_VALUE.startsWith(request.userInput().strip()) ? List.of(SYSTEM_VALUE) : List.of();
            }
            lookup = TrinoLookup.of(request.resource(), request.userInput(), request.context(), request.limit());
        }
        catch (IllegalArgumentException invalid) {
            throw new LookupException(LookupException.Reason.INVALID_INPUT, String.valueOf(invalid.getMessage()), invalid);
        }
        return clients.apply(settings).run(lookup::run);
    }
}

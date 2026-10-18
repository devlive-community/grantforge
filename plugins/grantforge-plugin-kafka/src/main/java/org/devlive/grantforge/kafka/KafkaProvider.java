// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.kafka;

import org.apache.kafka.clients.admin.DescribeClusterResult;
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

import java.util.List;

/**
 * The Apache Kafka service type. Its resources are Kafka's own resource types and its access types Kafka's own ACL
 * operations, with the same implications Kafka's authorizer applies ({@code read}, {@code write}, {@code delete} and
 * {@code alter} imply {@code describe}; {@code alter_configs} implies {@code describe_configs}), so a policy reads like
 * the ACL it replaces and the agent can show policies as ACLs. Each resource type offers the operations Kafka checks on
 * it. Names are matched with wildcards and with case; {@code orders-*} is Kafka's prefixed pattern. Lookups go through
 * Kafka's AdminClient.
 */
public final class KafkaProvider
        implements ServiceTypeProvider
{
    /** The service type's name. */
    public static final String TYPE = "kafka";

    static final String TOPIC = "topic";
    static final String GROUP = "group";
    static final String TRANSACTIONAL_ID = "transactional_id";
    static final String CLUSTER = "cluster";
    static final String DELEGATION_TOKEN = "delegation_token";
    static final String USER = "user";

    static final String READ = "read";
    static final String WRITE = "write";
    static final String CREATE = "create";
    static final String DELETE = "delete";
    static final String ALTER = "alter";
    static final String DESCRIBE = "describe";
    static final String CLUSTER_ACTION = "cluster_action";
    static final String DESCRIBE_CONFIGS = "describe_configs";
    static final String ALTER_CONFIGS = "alter_configs";
    static final String IDEMPOTENT_WRITE = "idempotent_write";
    static final String CREATE_TOKENS = "create_tokens";
    static final String DESCRIBE_TOKENS = "describe_tokens";
    static final String ALL = "all";

    @Override
    public ServiceTypeDefinition definition()
    {
        return ServiceTypeDefinition.builder(TYPE).label("Apache Kafka")
                .description("Topics, consumer groups, transactional IDs and the cluster of Kafka brokers")
                .resources(resource(TOPIC, "Topic", READ, WRITE, CREATE, DELETE, ALTER, DESCRIBE, DESCRIBE_CONFIGS, ALTER_CONFIGS, ALL),
                        resource(GROUP, "Group", READ, DELETE, DESCRIBE, ALL),
                        resource(TRANSACTIONAL_ID, "Transactional ID", WRITE, DESCRIBE, ALL),
                        resource(CLUSTER, "Cluster", CREATE, ALTER, DESCRIBE, CLUSTER_ACTION, DESCRIBE_CONFIGS, ALTER_CONFIGS, IDEMPOTENT_WRITE,
                                ALL),
                        resource(DELEGATION_TOKEN, "Delegation token", DESCRIBE, ALL),
                        resource(USER, "User", CREATE_TOKENS, DESCRIBE_TOKENS, ALL))
                .accessTypes(AccessTypeDefinition.of(READ, "Read", DESCRIBE), AccessTypeDefinition.of(WRITE, "Write", DESCRIBE),
                        AccessTypeDefinition.of(CREATE, "Create"), AccessTypeDefinition.of(DELETE, "Delete", DESCRIBE),
                        AccessTypeDefinition.of(ALTER, "Alter", DESCRIBE), AccessTypeDefinition.of(DESCRIBE, "Describe"),
                        AccessTypeDefinition.of(CLUSTER_ACTION, "Cluster action"), AccessTypeDefinition.of(DESCRIBE_CONFIGS, "Describe configs"),
                        AccessTypeDefinition.of(ALTER_CONFIGS, "Alter configs", DESCRIBE_CONFIGS),
                        AccessTypeDefinition.of(IDEMPOTENT_WRITE, "Idempotent write"),
                        AccessTypeDefinition.of(CREATE_TOKENS, "Create tokens"), AccessTypeDefinition.of(DESCRIBE_TOKENS, "Describe tokens"),
                        AccessTypeDefinition.of(ALL, "All", READ, WRITE, CREATE, DELETE, ALTER, DESCRIBE, CLUSTER_ACTION, DESCRIBE_CONFIGS,
                                ALTER_CONFIGS, IDEMPOTENT_WRITE, CREATE_TOKENS, DESCRIBE_TOKENS))
                .configFields(
                        ConfigField.builder(KafkaSettings.SERVERS).label("Bootstrap servers").type(ConfigFieldType.STRING).mandatory()
                                .description("Brokers to start from, host:port separated by commas, such as kafka1:9092,kafka2:9092").build(),
                        ConfigField.builder(KafkaSettings.PROTOCOL).label("Security protocol").type(ConfigFieldType.ENUM)
                                .options(KafkaSettings.PROTOCOLS.toArray(new String[0])).defaultValue("PLAINTEXT")
                                .description("How the brokers' listener is secured: TLS (SSL), SASL sign-in, or both").build(),
                        ConfigField.builder(KafkaSettings.MECHANISM).label("SASL mechanism").type(ConfigFieldType.ENUM)
                                .options(KafkaSettings.MECHANISMS.toArray(new String[0]))
                                .description("SASL protocols only: PLAIN or SCRAM with a password, GSSAPI for Kerberos").build(),
                        ConfigField.builder(KafkaSettings.USER).label("Username").type(ConfigFieldType.STRING)
                                .description("Who looks up topics and groups; with Kerberos the principal, such as grantforge@EXAMPLE.COM").build(),
                        ConfigField.builder(KafkaSettings.PASSWORD).label("Password").type(ConfigFieldType.SECRET)
                                .description("The user's password, or the principal's Kerberos password unless a keytab is given").build(),
                        ConfigField.builder(KafkaSettings.KEYTAB).label("Keytab").type(ConfigFieldType.STRING)
                                .description("Kerberos: path of the principal's keytab on the GrantForge server").build(),
                        ConfigField.builder(KafkaSettings.KDC).label("KDCs").type(ConfigFieldType.STRING)
                                .description("Kerberos: KDCs of the principal's realm, host[:port] separated by commas; empty uses the server's"
                                        + " krb5.conf").build(),
                        ConfigField.builder(KafkaSettings.SERVICE_NAME).label("Kerberos service name").type(ConfigFieldType.STRING)
                                .defaultValue("kafka").description("Kerberos: the first part of the brokers' principals").build(),
                        ConfigField.builder(KafkaSettings.TRUSTSTORE).label("Truststore").type(ConfigFieldType.STRING)
                                .description("TLS: path of the truststore that verifies the brokers; empty trusts what Java trusts").build(),
                        ConfigField.builder(KafkaSettings.TRUSTSTORE_PASSWORD).label("Truststore password").type(ConfigFieldType.SECRET).build(),
                        ConfigField.builder(KafkaSettings.TRUSTSTORE_TYPE).label("Truststore type").type(ConfigFieldType.ENUM)
                                .options(KafkaSettings.STORE_TYPES.toArray(new String[0])).defaultValue("JKS").build(),
                        ConfigField.builder(KafkaSettings.KEYSTORE).label("Keystore").type(ConfigFieldType.STRING)
                                .description("TLS: path of the keystore with GrantForge's certificate, for brokers that ask clients for one").build(),
                        ConfigField.builder(KafkaSettings.KEYSTORE_PASSWORD).label("Keystore password").type(ConfigFieldType.SECRET).build(),
                        ConfigField.builder(KafkaSettings.KEY_PASSWORD).label("Key password").type(ConfigFieldType.SECRET)
                                .description("The private key's password, when it is not the keystore's").build(),
                        ConfigField.builder(KafkaSettings.VERIFY_HOSTNAME).label("Verify broker hostnames").type(ConfigFieldType.BOOLEAN)
                                .defaultValue("true").description("TLS: whether the brokers' certificates must name the hosts connected to").build(),
                        ConfigField.builder(KafkaSettings.TIMEOUT).label("Request timeout (ms)").type(ConfigFieldType.INTEGER)
                                .defaultValue(Integer.toString(KafkaSettings.DEFAULT_TIMEOUT)).description("Between 1 and 600000").build())
                .build();
    }

    private static ResourceDefinition resource(String name, String label, String... accessTypes)
    {
        return ResourceDefinition.builder(name).label(label).matcher(MatcherType.WILDCARD).caseSensitive(true).excludesSupported(true)
                .lookupSupported(true).accessTypes(accessTypes).build();
    }

    @Override
    public List<ConfigProblem> validateConfig(ServiceConfig config)
    {
        return KafkaSettings.problems(config);
    }

    /** Signs in and describes the cluster: its id and how many brokers answer. */
    @Override
    public ConnectionResult testConnection(ServiceConfig config)
    {
        try {
            return new KafkaClient(KafkaSettings.of(config)).run(admin -> {
                DescribeClusterResult cluster = admin.describeCluster();
                String id = cluster.clusterId().get();
                int brokers = cluster.nodes().get().size();
                return new ConnectionResult(ConnectionResult.Status.SUCCEEDED, "cluster " + id + ", " + brokers + (brokers == 1 ? " broker" : " brokers"));
            });
        }
        catch (LookupException failed) {
            return ConnectionResult.failed(String.valueOf(failed.getMessage()));
        }
        catch (IllegalArgumentException invalid) {
            return ConnectionResult.failed(String.valueOf(invalid.getMessage()));
        }
    }

    /** Lists the names of a resource type that start with what was typed. */
    @Override
    public List<String> lookup(LookupRequest request)
    {
        KafkaSettings settings;
        try {
            settings = KafkaSettings.of(request.config());
        }
        catch (IllegalArgumentException invalid) {
            throw new LookupException(LookupException.Reason.INVALID_INPUT, String.valueOf(invalid.getMessage()), invalid);
        }
        if (!List.of(TOPIC, GROUP, TRANSACTIONAL_ID, CLUSTER, DELEGATION_TOKEN, USER).contains(request.resource())) {
            throw new LookupException(LookupException.Reason.INVALID_INPUT, "Kafka services look up no " + request.resource());
        }
        if (CLUSTER.equals(request.resource())) {
            return KafkaLookup.CLUSTER_NAME.startsWith(request.userInput().strip()) ? List.of(KafkaLookup.CLUSTER_NAME) : List.of();
        }
        KafkaLookup lookup = new KafkaLookup(request.resource(), request.userInput().strip(), request.limit());
        return new KafkaClient(settings).run(lookup::run);
    }
}

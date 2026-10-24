// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.solr.agent;

import inet.ipaddr.IPAddress;
import org.apache.solr.common.params.SolrParams;
import org.apache.solr.security.AuthorizationContext;
import org.apache.solr.security.AuthorizationPlugin;
import org.apache.solr.security.AuthorizationResponse;
import org.apache.solr.security.PermissionNameProvider;
import org.apache.solr.security.VerifiedUserRoles;
import org.devlive.grantforge.agent.AccessEvent;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.security.Principal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Solr's authorization plugin in GrantForge's terms, set as the {@code authorization} class of {@code security.json}.
 * Each request is classified by the permission name its handler gives it, as Solr's own rule-based plugin does: querying
 * needs {@code query}, updating {@code update}, schemas and configs their reading and editing types, at the collection
 * and at the collection with the request path. Collections API requests that name collections need
 * {@code admin_collection} on each of them, so a team may create, reload or back up its own collections; the other
 * administration needs the cluster's {@code health}, {@code metrics}, {@code admin_read}, {@code security} or
 * {@code admin}, and {@code admin} on the cluster grants everything. Every check goes with the client's address, for the
 * policy items that hold for some addresses only, and is reported.
 *
 * <p>Requests between the nodes, which Solr signs as the node itself, are not checked; a request without a user is asked
 * to sign in.
 */
@SuppressWarnings("PMD.AvoidUsingVolatile")
public class GrantForgeSolrAuthorizer
        implements AuthorizationPlugin
{
    static final String QUERY = "query";
    static final String UPDATE = "update";
    static final String SCHEMA_READ = "schema_read";
    static final String SCHEMA_EDIT = "schema_edit";
    static final String CONFIG_READ = "config_read";
    static final String CONFIG_EDIT = "config_edit";
    static final String ADMIN_COLLECTION = "admin_collection";
    static final String HEALTH = "health";
    static final String METRICS = "metrics";
    static final String ADMIN_READ = "admin_read";
    static final String SECURITY = "security";
    static final String ADMIN = "admin";

    /** The setting with the proxies whose {@code X-Forwarded-For} is trusted, addresses or ranges separated by commas. */
    static final String TRUSTED_PROXIES = SolrAgentRuntime.PREFIX + "trusted.proxies";

    /** The user Solr signs the requests between its nodes as. */
    static final String NODE = "$";

    private static final Logger LOG = LoggerFactory.getLogger(GrantForgeSolrAuthorizer.class);
    private static final String VERSION_FILE = "/META-INF/grantforge/solr-agent-version.properties";
    private static final int FORBIDDEN = 403;

    /** Solr's permission names of the read-only administration. */
    private static final Set<String> ADMIN_READING = Set.of("CORE_READ_PERM", "ZK_READ_PERM", "FILESTORE_READ_PERM", "PACKAGE_READ_PERM");

    private static final Holder SHARED = new Holder();

    private volatile @Nullable SolrAgentRuntime runtime;
    private volatile List<IPAddress> proxies = List.of();

    /** The agent the node's plugins share, and the settings it was started with. */
    private static final class Holder
    {
        final ReentrantLock lock = new ReentrantLock();
        @Nullable SolrAgentRuntime running;
        Map<String, String> settings = Map.of();
    }

    /** Creates the plugin Solr loads. */
    public GrantForgeSolrAuthorizer()
    {
        // Started by init.
    }

    GrantForgeSolrAuthorizer(SolrAgentRuntime runtime, List<IPAddress> proxies)
    {
        this.runtime = runtime;
        this.proxies = List.copyOf(proxies);
    }

    /**
     * Starts the agent from the {@code grantforge.*} settings of the authorization section. Solr creates the plugin anew
     * whenever {@code security.json} changes; the agent keeps running while its settings stay the same, and is replaced
     * when they change. It stops with the node's JVM, sending the events it folded.
     */
    @Override
    public void init(Map<String, Object> pluginConfig)
    {
        Map<String, String> settings = new HashMap<>();
        for (Map.Entry<String, Object> entry : pluginConfig.entrySet()) {
            if (entry.getKey().startsWith(SolrAgentRuntime.PREFIX) && entry.getValue() != null) {
                settings.put(entry.getKey(), setting(entry.getValue()));
            }
        }
        String trusted = settings.get(TRUSTED_PROXIES);
        proxies = trusted == null ? List.of() : AddressRange.parse(Arrays.asList(trusted.split(",")));
        runtime = shared(settings);
        LOG.info("GrantForge decides Solr's access; proxies trusted for X-Forwarded-For: {}", proxies);
    }

    private static String setting(Object value)
    {
        if (value instanceof Collection) {
            List<String> values = new ArrayList<>();
            for (Object each : (Collection<?>) value) {
                values.add(String.valueOf(each));
            }
            return String.join(",", values);
        }
        return String.valueOf(value);
    }

    // The runtime lives as long as the node, which stops it through the shutdown hook, or until settings replace it.
    @SuppressWarnings("PMD.DoNotUseThreads")
    private static SolrAgentRuntime shared(Map<String, String> settings)
    {
        SHARED.lock.lock();
        try {
            SolrAgentRuntime running = SHARED.running;
            if (running != null && SHARED.settings.equals(settings)) {
                return running;
            }
            SolrAgentRuntime started = SolrAgentRuntime.start(settings, version());
            Runtime.getRuntime().addShutdownHook(new Thread(started::close, "grantforge-solr-agent-stop"));
            SHARED.running = started;
            SHARED.settings = Map.copyOf(settings);
            if (running != null) {
                running.close();
            }
            return started;
        }
        catch (IOException broken) {
            throw new IllegalArgumentException("GrantForge cannot start the Solr agent: " + broken.getMessage(), broken);
        }
        finally {
            SHARED.lock.unlock();
        }
    }

    /**
     * Returns the agent's version, as the build wrote it.
     *
     * @return such as {@code 2026.3.0}
     * @throws IOException if the jar has no version
     */
    static String version() throws IOException
    {
        try (InputStream in = GrantForgeSolrAuthorizer.class.getResourceAsStream(VERSION_FILE)) {
            return version(in);
        }
    }

    /**
     * Reads the agent's version from the build's properties.
     *
     * @param in the properties, or {@code null} when the jar has none
     * @return the version
     * @throws IOException if there is none
     */
    static String version(@Nullable InputStream in) throws IOException
    {
        if (in == null) {
            throw new IOException("the agent jar has no " + VERSION_FILE);
        }
        Properties properties = new Properties();
        properties.load(in);
        String version = properties.getProperty("grantforge.version");
        String value = version == null ? "" : version.strip();
        if (value.isEmpty()) {
            throw new IOException(VERSION_FILE + " names no grantforge.version");
        }
        return value;
    }

    // The runtime is borrowed; the shutdown hook closes it.
    @Override
    @SuppressWarnings("PMD.CloseResource")
    public AuthorizationResponse authorize(AuthorizationContext context)
    {
        Principal principal = context.getUserPrincipal();
        if (principal == null) {
            return AuthorizationResponse.PROMPT;
        }
        String user = principal.getName();
        if (NODE.equals(user)) {
            return AuthorizationResponse.OK;
        }
        SolrAgentRuntime running = runtime;
        if (running == null) {
            return refuse("the GrantForge agent has not started");
        }
        try {
            Policies policies = running.policies();
            if (!policies.enforced()) {
                return AuthorizationResponse.OK;
            }
            Caller caller = new Caller(user, roles(principal), Caller.client(context.getRemoteAddr(), context.getHttpHeader("X-Forwarded-For"), proxies));
            for (Check check : checks(context)) {
                Policies.Verdict verdict = policies.decide(caller, check.resource, check.accessTypes);
                report(running, caller, check.resource, verdict, check.action, check.request);
                if (!verdict.allowed()) {
                    return refuse(why(verdict) + " for " + user + " to " + check.action + " " + check.resource.display());
                }
            }
            return AuthorizationResponse.OK;
        }
        catch (RuntimeException broken) {
            LOG.warn("GrantForge refuses {} to {}: {}", user, context.getResource(), broken.getMessage());
            return refuse("GrantForge cannot decide it");
        }
    }

    private static AuthorizationResponse refuse(String message)
    {
        AuthorizationResponse refused = new AuthorizationResponse(FORBIDDEN);
        refused.setMessage(message);
        return refused;
    }

    private static Set<String> roles(Principal principal)
    {
        if (principal instanceof VerifiedUserRoles) {
            Set<String> roles = ((VerifiedUserRoles) principal).getVerifiedRoles();
            return roles == null ? Set.of() : roles;
        }
        return Set.of();
    }

    /** One decision a request needs. */
    static final class Check
    {
        final SolrResource resource;
        final String action;
        final @Nullable String request;
        final String[] accessTypes;

        Check(SolrResource resource, String action, @Nullable String request, String... accessTypes)
        {
            this.resource = resource;
            this.action = action;
            this.request = request;
            this.accessTypes = accessTypes.clone();
        }
    }

    /**
     * Returns the decisions a request needs: one for each collection it reads or changes, or one for the cluster.
     *
     * @param context the request
     * @return the checks, all of which must allow it
     */
    static List<Check> checks(AuthorizationContext context)
    {
        String permission = permission(context);
        String action = permission == null ? String.valueOf(context.getRequestType()).toLowerCase(Locale.ROOT) : words(permission);
        List<String> collections = collections(context);
        String path = text(context.getResource());
        String collectionType = collectionType(permission, context.getRequestType());
        List<Check> checks = new ArrayList<>();
        if (collectionType != null && !collections.isEmpty()) {
            for (String collection : collections) {
                checks.add(new Check(SolrResource.collection(collection, path), action, null, collectionType));
            }
            return checks;
        }
        String request = request(context.getParams());
        if ("COLL_EDIT_PERM".equals(permission) || "COLL_READ_PERM".equals(permission)) {
            List<String> named = named(context);
            if (!named.isEmpty()) {
                for (String collection : named) {
                    checks.add(new Check(SolrResource.collection(collection, null), action, request, ADMIN_COLLECTION));
                }
                return checks;
            }
        }
        checks.add(new Check(SolrResource.cluster(), action, request, clusterType(permission, context.getRequestType())));
        return checks;
    }

    /** The permission name the request's handler gives it, such as {@code READ_PERM}, or {@code null} when it gives none. */
    private static @Nullable String permission(AuthorizationContext context)
    {
        Object handler = context.getHandler();
        if (!(handler instanceof PermissionNameProvider)) {
            return null;
        }
        try {
            PermissionNameProvider.Name name = ((PermissionNameProvider) handler).getPermissionName(context);
            return name == null || "ALL".equals(name.name()) ? null : name.name();
        }
        catch (RuntimeException unnamed) {
            LOG.debug("{} names no permission for {}: {}", handler, context.getResource(), unnamed.getMessage());
            return null;
        }
    }

    /** The access type of a request to collections, or {@code null} for a request that is the cluster's. */
    private static @Nullable String collectionType(@Nullable String permission, AuthorizationContext.@Nullable RequestType type)
    {
        if (permission == null) {
            if (type == AuthorizationContext.RequestType.READ) {
                return QUERY;
            }
            return type == AuthorizationContext.RequestType.WRITE ? UPDATE : null;
        }
        switch (permission) {
            case "READ_PERM":
                return QUERY;
            case "UPDATE_PERM":
                return UPDATE;
            case "SCHEMA_READ_PERM":
                return SCHEMA_READ;
            case "SCHEMA_EDIT_PERM":
                return SCHEMA_EDIT;
            case "CONFIG_READ_PERM":
                return CONFIG_READ;
            case "CONFIG_EDIT_PERM":
                return CONFIG_EDIT;
            default:
                return null;
        }
    }

    /** The access type of a request that is the cluster's; one Solr does not name needs {@code admin}. */
    private static String clusterType(@Nullable String permission, AuthorizationContext.@Nullable RequestType type)
    {
        if (permission == null) {
            return type == AuthorizationContext.RequestType.READ ? ADMIN_READ : ADMIN;
        }
        switch (permission) {
            case "HEALTH_PERM":
                return HEALTH;
            case "METRICS_READ_PERM":
                return METRICS;
            case "SECURITY_READ_PERM":
            case "SECURITY_EDIT_PERM":
                return SECURITY;
            case "COLL_READ_PERM":
            case "SCHEMA_READ_PERM":
            case "CONFIG_READ_PERM":
                return ADMIN_READ;
            default:
                return ADMIN_READING.contains(permission) ? ADMIN_READ : ADMIN;
        }
    }

    private static List<String> collections(AuthorizationContext context)
    {
        Set<String> names = new LinkedHashSet<>();
        List<AuthorizationContext.CollectionRequest> requests = context.getCollectionRequests();
        if (requests != null) {
            for (AuthorizationContext.CollectionRequest request : requests) {
                String name = request == null ? null : text(request.collectionName);
                if (name != null) {
                    names.add(name);
                }
            }
        }
        return new ArrayList<>(names);
    }

    /** The collections a Collections API request names, by its {@code collection} or {@code name} parameter. */
    private static List<String> named(AuthorizationContext context)
    {
        Set<String> names = new LinkedHashSet<>(collections(context));
        SolrParams params = context.getParams();
        if (params != null) {
            for (String parameter : List.of("collection", "name")) {
                String[] values = params.getParams(parameter);
                if (values != null) {
                    for (String value : values) {
                        for (String name : value.split(",")) {
                            String collection = text(name);
                            if (collection != null) {
                                names.add(collection);
                            }
                        }
                    }
                }
            }
        }
        return new ArrayList<>(names);
    }

    /** What an administration request asks for, such as {@code action=RELOAD}. */
    private static @Nullable String request(@Nullable SolrParams params)
    {
        String action = params == null ? null : text(params.get("action"));
        return action == null ? null : "action=" + action.toUpperCase(Locale.ROOT);
    }

    /** Reports a check; Solr words its own refusal, so the log says which policy refused it too. */
    private static void report(SolrAgentRuntime running, Caller caller, SolrResource resource, Policies.Verdict verdict, String action,
            @Nullable String request)
    {
        if (!verdict.allowed()) {
            LOG.info("GrantForge refuses {} to {} {}: {}", caller.user(), action, resource.display(), why(verdict));
        }
        running.audit(AccessEvent.builder(caller.user(), resource.display(), verdict.accessType(), verdict.allowed()).decidedBy(verdict.decision())
                .enforcedByGrantForge().clientIp(caller.clientIp()).resourceType(resource.level()).action(action).request(request).build());
    }

    /**
     * Returns why the policies refuse something.
     *
     * @param verdict the verdict
     * @return such as {@code GrantForge policy 7 denies it}
     */
    static String why(Policies.Verdict verdict)
    {
        Long policy = verdict.decision().policyId();
        return verdict.denied() && policy != null ? "GrantForge policy " + policy + " denies it" : "no GrantForge policy allows it";
    }

    /** A permission name in Solr's own words: {@code COLL_EDIT_PERM} is {@code collection-admin-edit}. */
    private static String words(String permission)
    {
        PermissionNameProvider.Name name = PermissionNameProvider.Name.valueOf(permission);
        return name.getPermissionName();
    }

    private static @Nullable String text(@Nullable String value)
    {
        String text = value == null ? "" : value.strip();
        return text.isEmpty() ? null : text;
    }

    /** The agent is the node's and stops with it; one of Solr's plugin instances closing leaves it running for the next. */
    @Override
    public void close()
    {
        // Stopped by the shutdown hook init added, or replaced by an init with other settings.
    }

    @Override
    public String toString()
    {
        return "GrantForge";
    }
}

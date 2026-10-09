// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.service;

import org.devlive.grantforge.audit.application.AuditLog;
import org.devlive.grantforge.audit.application.AuditRecord;
import org.devlive.grantforge.audit.domain.AuditAction;
import org.devlive.grantforge.audit.domain.AuditOutcome;
import org.devlive.grantforge.common.error.CommonErrorCode;
import org.devlive.grantforge.common.error.FieldIssue;
import org.devlive.grantforge.common.error.GrantForgeException;
import org.devlive.grantforge.identity.application.SecretBox;
import org.devlive.grantforge.persistence.tenant.TenantContext;
import org.devlive.grantforge.plugin.api.ConnectionResult;
import org.devlive.grantforge.plugin.api.LookupException;
import org.devlive.grantforge.plugin.api.LookupRequest;
import org.devlive.grantforge.plugin.api.ServiceConfig;
import org.devlive.grantforge.plugin.api.model.ConfigField;
import org.devlive.grantforge.plugin.api.model.ConfigProblem;
import org.devlive.grantforge.plugin.api.model.ResourceDefinition;
import org.devlive.grantforge.plugin.api.model.ServiceTypeDefinition;
import org.devlive.grantforge.plugin.host.PluginCallException;
import org.devlive.grantforge.plugin.host.PluginRegistry;
import org.devlive.grantforge.service.domain.AccessEventRepository;
import org.devlive.grantforge.service.domain.AgentTokenRepository;
import org.devlive.grantforge.service.domain.ManagedService;
import org.devlive.grantforge.service.domain.ManagedServiceRepository;
import org.devlive.grantforge.service.domain.ServiceAgentRepository;
import org.devlive.grantforge.service.domain.ServicePolicyRepository;
import org.jspecify.annotations.Nullable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.regex.Pattern;

import static java.util.Objects.requireNonNull;

/**
 * The services of the bound tenant: listing, adding, changing and removing them, testing their connection and
 * looking up their resources through the plugin of their type. Configurations are checked against the type's fields
 * and by the plugin; secrets are stored sealed and never returned. Every method must be called with the actor's
 * tenant bound.
 */
@Service
public final class ServiceAdministration
{
    /** Service names. */
    public static final Pattern NAME = Pattern.compile("[a-z][a-z0-9_-]{1,63}");

    /** Most values one lookup returns. */
    public static final int MAX_LOOKUP = 100;

    /** The longest plugin message a failure shows. */
    static final int MAX_SHOWN = 300;

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final TypeReference<Map<String, String>> VALUES = new TypeReference<>()
    {
    };

    private final ManagedServiceRepository services;
    private final ServicePolicyRepository policies;
    private final AgentTokenRepository agentTokens;
    private final ServiceAgentRepository agents;
    private final AccessEventRepository accessEvents;
    private final PluginRegistry plugins;
    private final SecretBox secrets;
    private final AuditLog audit;
    private final TransactionTemplate transactions;

    /**
     * Creates the service.
     *
     * @param services the services of the bound tenant
     * @param policies the policies of the services, removed with them
     * @param agentTokens the agent tokens of the services, removed with them
     * @param agents the agents of the services, removed with them
     * @param accessEvents the access events of the services, removed with them
     * @param plugins the installed plugins and their service types
     * @param secrets seals and opens secrets
     * @param audit records every change
     * @param transactionManager opens transactions
     */
    public ServiceAdministration(ManagedServiceRepository services, ServicePolicyRepository policies, AgentTokenRepository agentTokens,
            ServiceAgentRepository agents, AccessEventRepository accessEvents, PluginRegistry plugins, SecretBox secrets, AuditLog audit,
            PlatformTransactionManager transactionManager)
    {
        this.services = requireNonNull(services, "services");
        this.policies = requireNonNull(policies, "policies");
        this.agentTokens = requireNonNull(agentTokens, "agentTokens");
        this.agents = requireNonNull(agents, "agents");
        this.accessEvents = requireNonNull(accessEvents, "accessEvents");
        this.plugins = requireNonNull(plugins, "plugins");
        this.secrets = requireNonNull(secrets, "secrets");
        this.audit = requireNonNull(audit, "audit");
        this.transactions = new TransactionTemplate(requireNonNull(transactionManager, "transactionManager"));
    }

    /**
     * Returns the service types of the active plugins, for choosing one.
     *
     * @return their definitions
     */
    public List<ServiceTypeDefinition> serviceTypes()
    {
        return plugins.serviceTypes();
    }

    /**
     * Lists the services.
     *
     * @return the services, by name
     */
    public List<ServiceView> list()
    {
        return requireNonNull(transactions.execute(status -> services.findAllByOrderByNameAsc().stream().map(this::view).toList()));
    }

    /**
     * Returns a service.
     *
     * @param id the service
     * @return the service
     * @throws GrantForgeException with {@link CommonErrorCode#NOT_FOUND} for an unknown service
     */
    public ServiceView find(long id)
    {
        return requireNonNull(transactions.execute(status -> view(require(id))));
    }

    /**
     * Adds a service.
     *
     * @param actorId the account asking
     * @param serviceType the service type's name
     * @param command the service's name, description and configuration
     * @return the service
     * @throws GrantForgeException with {@link ServiceErrorCode#TYPE_UNAVAILABLE}, {@link ServiceErrorCode#CONFIG_INVALID}
     *         listing the fields at fault, {@link ServiceErrorCode#NAME_TAKEN} or {@link CommonErrorCode#BAD_REQUEST}
     */
    public ServiceView create(long actorId, String serviceType, ServiceCommand command)
    {
        checkName(command);
        ServiceTypeDefinition definition = definition(serviceType);
        ServiceConfigs.Resolved config = checked(definition, command.name(), command.values(), Map.of());
        ServiceView created = write(() -> {
            requireFreeName(command.name(), null);
            ManagedService service = ManagedService.create(serviceType, command.name(), command.label().strip(),
                    optional(command.description()));
            service.enable(command.enabled());
            service.configure(json(config.plain()), json(config.secrets()));
            return view(services.saveAndFlush(service));
        });
        record(AuditAction.SERVICE_CREATED, actorId, created.id(), serviceType);
        return created;
    }

    /**
     * Changes a service; its type stays.
     *
     * @param actorId the account asking
     * @param id the service
     * @param command the new name, description and configuration; blank secrets keep the stored ones
     * @return the service
     * @throws GrantForgeException as {@link #create}, or with {@link CommonErrorCode#NOT_FOUND}
     */
    public ServiceView update(long actorId, long id, ServiceCommand command)
    {
        checkName(command);
        ManagedService current = requireNonNull(transactions.execute(status -> require(id)));
        ServiceTypeDefinition definition = definition(current.getServiceType());
        ServiceConfigs.Resolved config = checked(definition, command.name(), command.values(), values(current.getSecrets()));
        ServiceView updated = write(() -> {
            ManagedService service = require(id);
            requireFreeName(command.name(), id);
            service.describe(command.name(), command.label().strip(), optional(command.description()));
            service.enable(command.enabled());
            service.configure(json(config.plain()), json(config.secrets()));
            return view(services.saveAndFlush(service));
        });
        record(AuditAction.SERVICE_UPDATED, actorId, id, updated.name());
        return updated;
    }

    /**
     * Removes a service with its policies, agent tokens, agents and access events.
     *
     * @param actorId the account asking
     * @param id the service
     * @throws GrantForgeException with {@link CommonErrorCode#NOT_FOUND}
     */
    public void delete(long actorId, long id)
    {
        String name = write(() -> {
            ManagedService service = require(id);
            policies.removeService(id);
            agentTokens.removeService(id);
            agents.removeService(id);
            accessEvents.removeService(id);
            services.delete(service);
            return service.getName();
        });
        record(AuditAction.SERVICE_DELETED, actorId, id, name);
    }

    /**
     * Tests a configuration, saved or not, by asking the plugin to reach the system.
     *
     * @param serviceType the service type's name
     * @param serviceId the service whose stored secrets fill blank secrets, or {@code null} for a new one
     * @param name the service's name, for the plugin's messages
     * @param values the configuration
     * @return the outcome; a plugin failure is reported as a failed connection
     * @throws GrantForgeException with {@link ServiceErrorCode#TYPE_UNAVAILABLE}, {@link ServiceErrorCode#CONFIG_INVALID}
     *         or {@link CommonErrorCode#NOT_FOUND} for an unknown service
     */
    public ConnectionResult test(String serviceType, @Nullable Long serviceId, String name, Map<String, String> values)
    {
        Map<String, String> stored = serviceId == null ? Map.of()
                : values(requireNonNull(transactions.execute(status -> require(serviceId))).getSecrets());
        ServiceTypeDefinition definition = definition(serviceType);
        ServiceConfigs.Resolved config = checked(definition, name, values, stored);
        try {
            return plugins.call(serviceType, provider -> provider.testConnection(new ServiceConfig(name, config.effective())));
        }
        catch (PluginCallException failed) {
            return ConnectionResult.failed(String.valueOf(failed.getMessage()));
        }
    }

    /**
     * Looks up existing values of a resource level of a service, to complete what a user types.
     *
     * @param id the service
     * @param resource the resource level
     * @param userInput what the user typed so far
     * @param context values chosen for other levels
     * @param limit the most values wanted; at most {@value #MAX_LOOKUP}
     * @return the values the plugin found
     * @throws GrantForgeException with {@link CommonErrorCode#NOT_FOUND}, {@link ServiceErrorCode#TYPE_UNAVAILABLE},
     *         {@link ServiceErrorCode#LOOKUP_UNSUPPORTED} or {@link ServiceErrorCode#PLUGIN_FAILED}
     */
    public List<String> lookup(long id, String resource, String userInput, Map<String, List<String>> context, int limit)
    {
        ManagedService service = requireNonNull(transactions.execute(status -> require(id)));
        ServiceTypeDefinition definition = definition(service.getServiceType());
        Optional<ResourceDefinition> level = definition.resources().stream().filter(candidate -> candidate.name().equals(resource))
                .findFirst();
        if (level.isEmpty() || !level.get().lookupSupported()) {
            throw new GrantForgeException(ServiceErrorCode.LOOKUP_UNSUPPORTED, resource + " offers no lookup", resource);
        }
        ServiceConfigs.Resolved config = ServiceConfigs.resolve(definition, values(service.getConfig()), values(service.getSecrets()),
                secrets::seal, secrets::open);
        LookupRequest request = new LookupRequest(new ServiceConfig(service.getName(), config.effective()), resource, userInput,
                context, Math.max(1, Math.min(limit, MAX_LOOKUP)));
        try {
            List<String> found = plugins.call(service.getServiceType(), provider -> provider.lookup(request));
            return found.stream().limit(request.limit()).toList();
        }
        catch (PluginCallException failed) {
            throw lookupFailure(failed, definition, config.effective());
        }
    }

    /**
     * Names why a lookup failed: the reason a plugin of API 1.1 gave, or any other failure as the plugin failing.
     */
    static GrantForgeException lookupFailure(PluginCallException failed, ServiceTypeDefinition definition, Map<String, String> config)
    {
        if (failed.getKind() == PluginCallException.Kind.FAILED && failed.getCause() instanceof LookupException named) {
            ServiceErrorCode code = switch (named.getReason()) {
                case NOT_FOUND -> ServiceErrorCode.LOOKUP_NOT_FOUND;
                case ACCESS_DENIED -> ServiceErrorCode.LOOKUP_DENIED;
                case UNREACHABLE -> ServiceErrorCode.LOOKUP_UNREACHABLE;
                case AUTHENTICATION_FAILED -> ServiceErrorCode.LOOKUP_AUTHENTICATION_FAILED;
                case LIMIT_EXCEEDED -> ServiceErrorCode.LOOKUP_LIMIT_EXCEEDED;
                case INVALID_INPUT -> ServiceErrorCode.LOOKUP_INVALID_INPUT;
                case FAILED -> ServiceErrorCode.PLUGIN_FAILED;
            };
            return new GrantForgeException(code, String.valueOf(failed.getMessage()), failed, shown(named, definition, config));
        }
        return pluginFailure(failed, definition, config);
    }

    /** A plugin call that threw or timed out, with what the plugin said. */
    static GrantForgeException pluginFailure(PluginCallException failed, ServiceTypeDefinition definition, Map<String, String> config)
    {
        if (failed.getKind() == PluginCallException.Kind.TIMED_OUT) {
            return new GrantForgeException(ServiceErrorCode.PLUGIN_TIMED_OUT, String.valueOf(failed.getMessage()), failed);
        }
        Throwable cause = failed.getCause() == null ? failed : failed.getCause();
        return new GrantForgeException(ServiceErrorCode.PLUGIN_FAILED, String.valueOf(failed.getMessage()), failed,
                shown(cause, definition, config));
    }

    /**
     * What a plugin said about a failure, fit to show: its first line, at most {@value #MAX_SHOWN} characters, with the
     * service's secret settings blanked out in case the plugin echoed one.
     */
    static String shown(Throwable failure, ServiceTypeDefinition definition, Map<String, String> config)
    {
        String message = failure.getMessage();
        String text = message == null || message.isBlank() ? failure.getClass().getSimpleName() : message.strip();
        int line = text.indexOf('\n');
        text = line < 0 ? text : text.substring(0, line).strip();
        for (ConfigField field : definition.configFields()) {
            String secret = config.get(field.name());
            if (field.sensitive() && secret != null && !secret.isEmpty()) {
                text = text.replace(secret, "***");
            }
        }
        return text.length() <= MAX_SHOWN ? text : text.substring(0, MAX_SHOWN - 1) + "…";
    }

    /** Checks a configuration against the fields and then with the plugin; throws with every issue found. */
    private ServiceConfigs.Resolved checked(ServiceTypeDefinition definition, String name, Map<String, String> given,
            Map<String, String> stored)
    {
        ServiceConfigs.Resolved config = ServiceConfigs.resolve(definition, given, stored, secrets::seal, secrets::open);
        List<FieldIssue> issues = new ArrayList<>(config.issues());
        if (issues.isEmpty()) {
            List<ConfigProblem> problems;
            try {
                problems = plugins.call(definition.name(), provider -> provider.validateConfig(new ServiceConfig(name, config.effective())));
            }
            catch (PluginCallException failed) {
                throw pluginFailure(failed, definition, config.effective());
            }
            issues.addAll(ServiceConfigs.issues(problems));
        }
        if (!issues.isEmpty()) {
            throw new GrantForgeException(ServiceErrorCode.CONFIG_INVALID, issues.size() + " configuration issues")
                    .withFieldIssues(issues);
        }
        return config;
    }

    private ServiceTypeDefinition definition(String serviceType)
    {
        return plugins.serviceType(serviceType).orElseThrow(() -> new GrantForgeException(ServiceErrorCode.TYPE_UNAVAILABLE,
                "no active plugin provides " + serviceType, serviceType));
    }

    private static void checkName(ServiceCommand command)
    {
        List<FieldIssue> issues = new ArrayList<>();
        if (!NAME.matcher(command.name()).matches()) {
            issues.add(FieldIssue.of("name", "error.service.config.pattern-mismatch"));
        }
        if (command.label().isBlank() || command.label().strip().length() > 128) {
            issues.add(FieldIssue.of("label", "error.service.config.required"));
        }
        String description = command.description();
        if (description != null && description.length() > 512) {
            issues.add(FieldIssue.of("description", "error.service.config.pattern-mismatch"));
        }
        if (!issues.isEmpty()) {
            throw new GrantForgeException(CommonErrorCode.BAD_REQUEST, "invalid service name or label").withFieldIssues(issues);
        }
    }

    private void requireFreeName(String name, @Nullable Long except)
    {
        services.findByName(name).filter(other -> except == null || other.requireId() != except).ifPresent(other -> {
            throw new GrantForgeException(ServiceErrorCode.NAME_TAKEN, "service name taken", name);
        });
    }

    private ManagedService require(long id)
    {
        return services.findById(id).orElseThrow(() -> new GrantForgeException(CommonErrorCode.NOT_FOUND, "no service " + id));
    }

    private ServiceView view(ManagedService service)
    {
        String label = plugins.serviceType(service.getServiceType()).map(ServiceTypeDefinition::label).orElse(null);
        return new ServiceView(service.requireId(), service.getName(), service.getLabel(), service.getDescription(),
                service.getServiceType(), label, service.isEnabled(), values(service.getConfig()), values(service.getSecrets()).keySet());
    }

    private <T> T write(Supplier<T> change)
    {
        try {
            return requireNonNull(transactions.execute(status -> change.get()));
        }
        catch (DataIntegrityViolationException race) {
            throw new GrantForgeException(CommonErrorCode.CONFLICT, "service changed concurrently", race);
        }
    }

    private static @Nullable String optional(@Nullable String value)
    {
        return value == null || value.isBlank() ? null : value.strip();
    }

    private static String json(Map<String, String> values)
    {
        return JSON.writeValueAsString(values);
    }

    private static Map<String, String> values(String json)
    {
        try {
            return JSON.readValue(json, VALUES);
        }
        catch (JacksonException broken) {
            throw new IllegalStateException("stored service configuration is not valid JSON", broken);
        }
    }

    private void record(AuditAction action, long actorId, long serviceId, String reason)
    {
        audit.record(new AuditRecord(action, AuditOutcome.SUCCESS, TenantContext.requireTenantId(), actorId, null,
                Long.toString(serviceId), reason));
    }
}

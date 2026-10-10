// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.service;

import org.devlive.grantforge.common.error.CommonErrorCode;
import org.devlive.grantforge.common.error.GrantForgeException;
import org.devlive.grantforge.identity.domain.Tenant;
import org.devlive.grantforge.identity.domain.TenantRepository;
import org.devlive.grantforge.persistence.tenant.TenantContext;
import org.devlive.grantforge.plugin.api.model.ServiceTypeDefinition;
import org.devlive.grantforge.plugin.host.InstalledPlugin;
import org.devlive.grantforge.plugin.host.PluginRegistry;
import org.devlive.grantforge.service.domain.ManagedService;
import org.devlive.grantforge.service.domain.ManagedServiceRepository;
import org.devlive.grantforge.service.domain.ServiceAgentRepository;
import org.devlive.grantforge.service.domain.ServicePolicyRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static java.util.Objects.requireNonNull;

/**
 * Works out what switching a plugin off would leave without its service types: the services of every tenant that use
 * them, with their policies and agents. Those services stay, but nothing can be done with them until the plugin is
 * switched on again: their settings cannot be edited or tested, their policies cannot be changed, and their agents
 * get no new policies, enforcing the last ones they fetched.
 */
@Service
public final class PluginImpact
{
    private final PluginRegistry plugins;
    private final TenantRepository tenants;
    private final ManagedServiceRepository services;
    private final ServicePolicyRepository policies;
    private final ServiceAgentRepository agents;
    private final TransactionTemplate reading;

    /**
     * Creates the analysis.
     *
     * @param plugins the installed plugins
     * @param tenants every tenant, since a plugin serves them all
     * @param services the services of the bound tenant
     * @param policies the policies of the bound tenant's services
     * @param agents the agents of the bound tenant's services
     * @param transactionManager opens a read-only transaction for each tenant
     */
    public PluginImpact(PluginRegistry plugins, TenantRepository tenants, ManagedServiceRepository services, ServicePolicyRepository policies,
            ServiceAgentRepository agents, PlatformTransactionManager transactionManager)
    {
        this.plugins = requireNonNull(plugins, "plugins");
        this.tenants = requireNonNull(tenants, "tenants");
        this.services = requireNonNull(services, "services");
        this.policies = requireNonNull(policies, "policies");
        this.agents = requireNonNull(agents, "agents");
        this.reading = new TransactionTemplate(requireNonNull(transactionManager, "transactionManager"));
        reading.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        reading.setReadOnly(true);
    }

    /**
     * Works out what switching a plugin off would affect.
     *
     * @param pluginId the plugin
     * @return the service types it provides and the services of every tenant using them, by tenant and then by name
     * @throws GrantForgeException with {@link CommonErrorCode#NOT_FOUND} for an unknown plugin
     */
    public Report ofDisabling(String pluginId)
    {
        InstalledPlugin plugin = plugins.plugins().stream().filter(candidate -> candidate.id().equals(pluginId)).findFirst()
                .orElseThrow(() -> new GrantForgeException(CommonErrorCode.NOT_FOUND, "no plugin " + pluginId));
        // A plugin provides service types only while it is active; one that is not affects nothing more by being disabled.
        Set<String> types = plugin.serviceTypes().stream().map(ServiceTypeDefinition::name).collect(Collectors.toUnmodifiableSet());
        List<AffectedService> affected = new ArrayList<>();
        if (!types.isEmpty()) {
            List<Tenant> every = requireNonNull(TenantContext.callAsSystem(() -> reading.execute(status -> tenants.findAll())));
            for (Tenant tenant : every) {
                affected.addAll(requireNonNull(TenantContext.callInTenant(tenant.requireId(), () -> reading.execute(status ->
                        services.findAllByOrderByNameAsc().stream().filter(service -> types.contains(service.getServiceType()))
                                .map(service -> affected(tenant, service)).toList()))));
            }
        }
        return new Report(plugin.id(), plugin.name(), types.stream().sorted().toList(), affected);
    }

    private AffectedService affected(Tenant tenant, ManagedService service)
    {
        long id = service.requireId();
        return new AffectedService(tenant.getCode(), tenant.getName(), id, service.getName(), service.getLabel(), service.getServiceType(),
                service.isEnabled(), policies.countByServiceId(id), agents.countByServiceId(id));
    }

    /**
     * What switching a plugin off would affect.
     *
     * @param pluginId the plugin's id
     * @param pluginName what the console calls it
     * @param serviceTypes the names of the service types it provides, which become unavailable
     * @param services the services of every tenant using them
     */
    public record Report(String pluginId, String pluginName, List<String> serviceTypes, List<AffectedService> services)
    {
        /** Copies the lists. */
        public Report
        {
            requireNonNull(pluginId, "pluginId");
            requireNonNull(pluginName, "pluginName");
            serviceTypes = List.copyOf(serviceTypes);
            services = List.copyOf(services);
        }
    }

    /**
     * A service left without its service type.
     *
     * @param tenantCode the code of the tenant it belongs to
     * @param tenantName that tenant's name
     * @param serviceId the service
     * @param name its name
     * @param label what the console shows
     * @param serviceType the service type it uses
     * @param enabled whether it is in use
     * @param policies how many policies it has, which can no longer be changed
     * @param agents how many agents enforce it, which get no new policies
     */
    public record AffectedService(String tenantCode, String tenantName, long serviceId, String name, String label, String serviceType,
            boolean enabled, long policies, long agents)
    {
    }
}

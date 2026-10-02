// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.service;

import jakarta.validation.Valid;
import org.devlive.grantforge.common.security.RequirePermission;
import org.devlive.grantforge.server.security.SessionUser;
import org.devlive.grantforge.server.web.PathIds;
import org.devlive.grantforge.service.ServiceAdministration;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static java.util.Objects.requireNonNull;
import static java.util.Objects.requireNonNullElse;

/** The services of the signed-in user's tenant whose permissions plugins manage. */
@RestController
public final class ServiceController
{
    private final ServiceAdministration services;

    /**
     * Creates the controller.
     *
     * @param services the services
     */
    public ServiceController(ServiceAdministration services)
    {
        this.services = requireNonNull(services, "services");
    }

    /**
     * Lists the service types of the active plugins.
     *
     * @return the types, with their settings and resource levels
     */
    @RequirePermission("data.service.read")
    @GetMapping("/api/v1/service-types")
    public List<ServiceTypeResponse> serviceTypes()
    {
        return services.serviceTypes().stream().map(ServiceTypeResponse::from).toList();
    }

    /**
     * Lists the services.
     *
     * @return the services, by name
     */
    @RequirePermission("data.service.read")
    @GetMapping("/api/v1/services")
    public List<ServiceResponse> list()
    {
        return services.list().stream().map(ServiceResponse::from).toList();
    }

    /**
     * Returns a service.
     *
     * @param id the service
     * @return the service
     */
    @RequirePermission("data.service.read")
    @GetMapping("/api/v1/services/{id}")
    public ServiceResponse find(@PathVariable String id)
    {
        return ServiceResponse.from(services.find(PathIds.parse(id, "service")));
    }

    /**
     * Adds a service.
     *
     * @param user the session's principal
     * @param body the service
     * @return the service
     */
    @RequirePermission("data.service.create")
    @PostMapping("/api/v1/services")
    @ResponseStatus(HttpStatus.CREATED)
    public ServiceResponse create(@AuthenticationPrincipal SessionUser user, @Valid @RequestBody ServiceRequest body)
    {
        return ServiceResponse.from(services.create(user.accountId(), String.valueOf(body.serviceType()), body.command()));
    }

    /**
     * Changes a service.
     *
     * @param user the session's principal
     * @param id the service
     * @param body the new name, description and settings
     * @return the service
     */
    @RequirePermission("data.service.update")
    @PutMapping("/api/v1/services/{id}")
    public ServiceResponse update(@AuthenticationPrincipal SessionUser user, @PathVariable String id,
            @Valid @RequestBody ServiceRequest body)
    {
        return ServiceResponse.from(services.update(user.accountId(), PathIds.parse(id, "service"), body.command()));
    }

    /**
     * Removes a service.
     *
     * @param user the session's principal
     * @param id the service
     */
    @RequirePermission("data.service.delete")
    @DeleteMapping("/api/v1/services/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal SessionUser user, @PathVariable String id)
    {
        services.delete(user.accountId(), PathIds.parse(id, "service"));
    }

    /**
     * Tests a configuration, saved or not.
     *
     * @param body the configuration
     * @return how the test went
     */
    @RequirePermission("data.service.test")
    @PostMapping("/api/v1/services/test")
    public ConnectionResponse test(@Valid @RequestBody ConnectionTestRequest body)
    {
        String serviceId = body.serviceId();
        Long existing = serviceId == null || serviceId.isBlank() ? null : PathIds.parse(serviceId.strip(), "service");
        return ConnectionResponse.from(services.test(String.valueOf(body.serviceType()), existing,
                requireNonNullElse(body.name(), "new"), body.values()));
    }

    /**
     * Looks up existing values of a resource level of a service.
     *
     * @param id the service
     * @param body what to look up
     * @return the values found
     */
    @RequirePermission("data.service.read")
    @PostMapping("/api/v1/services/{id}/lookup")
    public List<String> lookup(@PathVariable String id, @Valid @RequestBody LookupRequestBody body)
    {
        return services.lookup(PathIds.parse(id, "service"), String.valueOf(body.resource()), requireNonNullElse(body.userInput(), ""),
                body.context(), requireNonNullElse(body.limit(), 20));
    }
}

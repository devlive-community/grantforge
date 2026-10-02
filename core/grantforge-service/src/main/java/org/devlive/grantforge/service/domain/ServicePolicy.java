// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.service.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import org.devlive.grantforge.persistence.tenant.TenantScopedEntity;
import org.devlive.grantforge.plugin.api.model.PolicyType;
import org.jspecify.annotations.Nullable;

import static java.util.Objects.requireNonNull;

/**
 * A policy of a service: whom it allows or denies what on which resources, or how it masks or filters them. What it
 * covers and its items are one JSON document ({@code body}); its labels a JSON array.
 */
@Entity
@Table(name = "gf_policy")
public class ServicePolicy
        extends TenantScopedEntity
{
    @Column(name = "service_id", nullable = false, updatable = false)
    private long serviceId;

    @Column(name = "name", nullable = false, length = 128)
    private String name = "";

    @Column(name = "description", length = 512)
    private @Nullable String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "policy_type", nullable = false, updatable = false, length = 16)
    private PolicyType policyType = PolicyType.ACCESS;

    @Enumerated(EnumType.STRING)
    @Column(name = "priority", nullable = false, length = 16)
    private PolicyPriority priority = PolicyPriority.NORMAL;

    @Column(name = "enabled", nullable = false)
    private boolean enabled = true;

    @Column(name = "labels", nullable = false, length = 1024)
    private String labels = "[]";

    @Column(name = "body", nullable = false)
    private String body = "{}";

    /** For JPA. */
    protected ServicePolicy()
    {
    }

    /**
     * Creates a policy.
     *
     * @param serviceId the service it belongs to
     * @param policyType what kind of policy it is
     * @return the policy, enabled and of normal priority; describe it before saving
     */
    public static ServicePolicy create(long serviceId, PolicyType policyType)
    {
        ServicePolicy policy = new ServicePolicy();
        policy.serviceId = serviceId;
        policy.policyType = requireNonNull(policyType, "policyType");
        return policy;
    }

    /**
     * Changes what the policy says.
     *
     * @param newName its name, unique within the service
     * @param newDescription a longer explanation, or {@code null}
     * @param newPriority whether it overrides normal policies
     * @param on whether it is in use
     * @param newLabels its labels, as a JSON array
     * @param newBody what it covers and its items, as JSON
     */
    public void describe(String newName, @Nullable String newDescription, PolicyPriority newPriority, boolean on, String newLabels,
            String newBody)
    {
        this.name = requireNonNull(newName, "name");
        this.description = newDescription;
        this.priority = requireNonNull(newPriority, "priority");
        this.enabled = on;
        this.labels = requireNonNull(newLabels, "labels");
        this.body = requireNonNull(newBody, "body");
    }

    /**
     * Returns the service the policy belongs to.
     *
     * @return the service's id
     */
    public long getServiceId()
    {
        return serviceId;
    }

    /**
     * Returns the name.
     *
     * @return the name
     */
    public String getName()
    {
        return name;
    }

    /**
     * Returns the longer explanation.
     *
     * @return the description, or {@code null}
     */
    public @Nullable String getDescription()
    {
        return description;
    }

    /**
     * Returns what kind of policy it is.
     *
     * @return the type
     */
    public PolicyType getPolicyType()
    {
        return policyType;
    }

    /**
     * Returns whether the policy overrides normal ones.
     *
     * @return the priority
     */
    public PolicyPriority getPriority()
    {
        return priority;
    }

    /**
     * Returns whether the policy is in use.
     *
     * @return {@code true} if enabled
     */
    public boolean isEnabled()
    {
        return enabled;
    }

    /**
     * Returns the labels.
     *
     * @return a JSON array
     */
    public String getLabels()
    {
        return labels;
    }

    /**
     * Returns what the policy covers and its items.
     *
     * @return JSON
     */
    public String getBody()
    {
        return body;
    }
}

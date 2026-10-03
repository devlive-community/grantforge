// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.data;

import jakarta.validation.Valid;
import org.devlive.grantforge.authz.application.DataEntityCatalog;
import org.devlive.grantforge.authz.data.DataPolicyService;
import org.devlive.grantforge.authz.data.DataScopes;
import org.devlive.grantforge.common.security.RequirePermission;
import org.devlive.grantforge.persistence.secured.SecuredEntities;
import org.devlive.grantforge.server.security.SessionUser;
import org.devlive.grantforge.server.web.PathIds;
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

/** Which rows of the secured entities roles may read, change, delete or export. */
@RestController
public final class DataPolicyController
{
    private final DataPolicyService policies;
    private final SecuredEntities entities;
    private final DataScopes scopes;
    private final DataEntityCatalog catalog;

    /**
     * Creates the controller.
     *
     * @param policies the data policies
     * @param entities the secured entities
     * @param scopes previews what policies show
     * @param catalog the secured fields the code declares
     */
    public DataPolicyController(DataPolicyService policies, SecuredEntities entities, DataScopes scopes, DataEntityCatalog catalog)
    {
        this.catalog = requireNonNull(catalog, "catalog");
        this.policies = requireNonNull(policies, "policies");
        this.entities = requireNonNull(entities, "entities");
        this.scopes = requireNonNull(scopes, "scopes");
    }

    /**
     * Describes what data and field policies can say: the secured entities, their scopes, fields and secured fields, and the
     * condition variables.
     *
     * @return the description
     */
    @RequirePermission("system.role.read")
    @GetMapping("/api/v1/data-entities")
    public DataEntitiesResponse entities()
    {
        return DataEntitiesResponse.from(entities.all(), catalog.securedFields());
    }

    /**
     * Lists the data policies of a role.
     *
     * @param id the role
     * @return the policies, by entity
     */
    @RequirePermission("system.role.read")
    @GetMapping("/api/v1/roles/{id}/data-policies")
    public List<DataPolicyResponse> list(@PathVariable String id)
    {
        return policies.list(PathIds.parse(id, "role")).stream().map(DataPolicyResponse::from).toList();
    }

    /**
     * Adds a data policy to a role.
     *
     * @param user the session's principal
     * @param id the role
     * @param body the policy
     * @return the policy
     */
    @RequirePermission("system.role.data")
    @PostMapping("/api/v1/roles/{id}/data-policies")
    @ResponseStatus(HttpStatus.CREATED)
    public DataPolicyResponse create(@AuthenticationPrincipal SessionUser user, @PathVariable String id,
            @Valid @RequestBody DataPolicyRequest body)
    {
        return DataPolicyResponse.from(policies.create(user.accountId(), PathIds.parse(id, "role"), body.command()));
    }

    /**
     * Changes a data policy.
     *
     * @param user the session's principal
     * @param id the policy
     * @param body the policy
     * @return the policy
     */
    @RequirePermission("system.role.data")
    @PutMapping("/api/v1/data-policies/{id}")
    public DataPolicyResponse update(@AuthenticationPrincipal SessionUser user, @PathVariable String id,
            @Valid @RequestBody DataPolicyRequest body)
    {
        return DataPolicyResponse.from(policies.update(user.accountId(), PathIds.parse(id, "data policy"), body.command()));
    }

    /**
     * Removes a data policy.
     *
     * @param user the session's principal
     * @param id the policy
     */
    @RequirePermission("system.role.data")
    @DeleteMapping("/api/v1/data-policies/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal SessionUser user, @PathVariable String id)
    {
        policies.delete(user.accountId(), PathIds.parse(id, "data policy"));
    }

    /**
     * Counts the rows a user would see with only a role, against now, to try its data policies before assigning it.
     *
     * @param id the role
     * @param body the user, entity and action
     * @return both counts
     */
    @RequirePermission("system.role.data")
    @PostMapping("/api/v1/roles/{id}/data-policies/preview")
    public DataPreviewResponse preview(@PathVariable String id, @Valid @RequestBody DataPreviewRequest body)
    {
        return DataPreviewResponse.from(scopes.preview(PathIds.parse(id, "role"), Long.parseLong(String.valueOf(body.accountId())),
                String.valueOf(body.entityCode()), requireNonNull(body.action(), "action")));
    }
}

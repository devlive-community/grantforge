// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.sdk;

import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;

import java.util.List;
import java.util.Optional;

import static java.util.Objects.requireNonNull;

/**
 * What a user's roles say about this application's data entities, as GrantForge answers it. A row is usable for an
 * action if an allowing rule covers it and no denying rule does; an entity or action without rules allows nothing.
 *
 * @param subject the user, as rules refer to them
 * @param entities the rules by entity and action
 * @param version a fingerprint of the rest, which changes when the rules do
 */
public record UserDataAccess(Subject subject, List<EntityRules> entities, long version)
{
    /** Checks and copies the parts. */
    public UserDataAccess
    {
        requireNonNull(subject, "subject");
        entities = List.copyOf(entities);
    }

    /**
     * Returns the rules of an action on an entity.
     *
     * @param entity the entity's code, as declared with {@link GrantForgeEntity}
     * @param action the action
     * @return the rules, or empty if none: nothing is allowed then
     */
    public Optional<EntityRules> rules(String entity, DataAction action)
    {
        return entities.stream().filter(rules -> rules.entity().equals(entity) && rules.action() == action).findFirst();
    }

    /**
     * The user, as rules refer to them.
     *
     * @param accountId the account, compared with owner columns
     * @param tenantId the tenant, compared with tenant columns
     * @param username the login name
     * @param orgUnitIds the user's departments
     * @param orgUnitsAndBelow those departments and every department below them
     * @param groupCodes the user's groups
     * @param positionCodes the user's positions
     */
    public record Subject(String accountId, String tenantId, String username, List<String> orgUnitIds, List<String> orgUnitsAndBelow,
            List<String> groupCodes, List<String> positionCodes)
    {
        /** Checks and copies the parts. */
        public Subject
        {
            requireNonNull(accountId, "accountId");
            requireNonNull(tenantId, "tenantId");
            requireNonNull(username, "username");
            orgUnitIds = List.copyOf(orgUnitIds);
            orgUnitsAndBelow = List.copyOf(orgUnitsAndBelow);
            groupCodes = List.copyOf(groupCodes);
            positionCodes = List.copyOf(positionCodes);
        }
    }

    /**
     * The rules of one action on one entity.
     *
     * @param entity the entity's code
     * @param action the action
     * @param allow rules allowing rows
     * @param deny rules denying rows, which win
     */
    public record EntityRules(String entity, DataAction action, List<Rule> allow, List<Rule> deny)
    {
        /** Checks and copies the parts. */
        public EntityRules
        {
            requireNonNull(entity, "entity");
            requireNonNull(action, "action");
            allow = List.copyOf(allow);
            deny = List.copyOf(deny);
        }
    }

    /**
     * Which rows a rule covers.
     *
     * @param scope the scope
     * @param condition the condition of a {@link DataScope#CONDITION} rule, or {@code null}
     * @param orgUnitIds the chosen departments of a {@link DataScope#CUSTOM_ORGS} rule
     */
    public record Rule(DataScope scope, @Nullable JsonNode condition, List<String> orgUnitIds)
    {
        /** Checks and copies the parts. */
        public Rule
        {
            requireNonNull(scope, "scope");
            orgUnitIds = List.copyOf(orgUnitIds);
        }
    }
}

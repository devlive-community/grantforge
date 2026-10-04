// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.open;

import org.devlive.grantforge.authz.data.ApplicationAccess;
import org.devlive.grantforge.authz.data.ConditionCodec;
import org.devlive.grantforge.authz.data.DataAccess;
import org.devlive.grantforge.authz.data.DataRule;
import org.devlive.grantforge.authz.data.DataSubject;
import org.devlive.grantforge.persistence.secured.DataAction;
import org.devlive.grantforge.persistence.secured.DataScope;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static java.util.Objects.requireNonNull;

/**
 * What the user's roles say about the calling application's data entities, for the application to apply to its own rows.
 * A row is usable for an action if some allowing rule covers it and no denying rule does; an entity or action without
 * rules allows nothing. IDs are strings because they exceed JavaScript's safe integers.
 *
 * @param subject the user, as rules refer to them
 * @param entities the rules by entity and action
 * @param version a fingerprint of the rest, also the response's ETag
 */
public record OpenDataAccessResponse(Subject subject, List<EntityRules> entities, long version)
{
    private static final JsonMapper JSON = JsonMapper.builder().build();

    /** Checks and copies the parts. */
    public OpenDataAccessResponse
    {
        requireNonNull(subject, "subject");
        entities = List.copyOf(entities);
    }

    /**
     * Converts what the roles say.
     *
     * @param access the access
     * @return the response
     */
    public static OpenDataAccessResponse from(ApplicationAccess access)
    {
        DataSubject subject = access.subject();
        Subject described = new Subject(Long.toString(subject.accountId()), Long.toString(subject.tenantId()), subject.username(),
                ids(subject.orgUnitIds()), ids(access.orgUnitsAndBelow()), subject.groupCodes(), subject.positionCodes());
        List<EntityRules> entities = access.rules().entrySet().stream()
                .sorted(Comparator.comparing((Map.Entry<DataAccess.Key, DataAccess.Rules> entry) -> entry.getKey().entityCode())
                        .thenComparing(entry -> entry.getKey().action()))
                .map(entry -> new EntityRules(entry.getKey().entityCode(), entry.getKey().action(), rules(entry.getValue().allow()),
                        rules(entry.getValue().deny())))
                .toList();
        return new OpenDataAccessResponse(described, entities, Integer.toUnsignedLong(Objects.hash(described, entities)));
    }

    private static List<Rule> rules(List<DataRule> rules)
    {
        return rules.stream().map(rule -> new Rule(rule.scope(), rule.condition() == null ? null
                : JSON.readTree(ConditionCodec.write(requireNonNull(rule.condition()))), ids(rule.orgUnitIds()))).toList();
    }

    private static List<String> ids(Collection<Long> ids)
    {
        return ids.stream().map(id -> Long.toString(id)).toList();
    }

    /**
     * The user, as rules refer to them.
     *
     * @param accountId the account, which "own rows" compares the owner column with
     * @param tenantId the tenant, which "the tenant" compares a tenant column with
     * @param username the login name, for conditions
     * @param orgUnitIds the departments the user belongs to
     * @param orgUnitsAndBelow those departments and every department below them
     * @param groupCodes the user's groups, for conditions
     * @param positionCodes the user's positions, for conditions
     */
    public record Subject(String accountId, String tenantId, String username, List<String> orgUnitIds, List<String> orgUnitsAndBelow,
            List<String> groupCodes, List<String> positionCodes)
    {
        /** Copies the lists. */
        public Subject
        {
            orgUnitIds = List.copyOf(orgUnitIds);
            orgUnitsAndBelow = List.copyOf(orgUnitsAndBelow);
            groupCodes = List.copyOf(groupCodes);
            positionCodes = List.copyOf(positionCodes);
        }
    }

    /**
     * The rules of one action on one entity.
     *
     * @param entity the entity's code in the application
     * @param action the action
     * @param allow rules allowing rows
     * @param deny rules denying rows, which win
     */
    public record EntityRules(String entity, DataAction action, List<Rule> allow, List<Rule> deny)
    {
        /** Copies the lists. */
        public EntityRules
        {
            allow = List.copyOf(allow);
            deny = List.copyOf(deny);
        }
    }

    /**
     * Which rows a rule covers: all, the tenant's, the user's own, those of the user's departments (and below), of chosen
     * departments, or those a condition selects.
     *
     * @param scope the scope
     * @param condition the condition of a {@code CONDITION} rule, in GrantForge's condition grammar, or {@code null}
     * @param orgUnitIds the chosen departments of a {@code CUSTOM_ORGS} rule
     */
    public record Rule(DataScope scope, @Nullable JsonNode condition, List<String> orgUnitIds)
    {
        /** Copies the list. */
        public Rule
        {
            orgUnitIds = List.copyOf(orgUnitIds);
        }
    }
}

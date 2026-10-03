// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.authz;

import org.devlive.grantforge.authz.application.EffectiveAccess;
import org.devlive.grantforge.authz.application.EffectiveRole;
import org.devlive.grantforge.authz.data.DataAccess;
import org.devlive.grantforge.authz.data.DataRule;
import org.devlive.grantforge.authz.domain.ResourceType;
import org.devlive.grantforge.persistence.secured.DataAction;
import org.devlive.grantforge.persistence.secured.DataScope;
import org.devlive.grantforge.persistence.secured.FieldMode;
import org.devlive.grantforge.server.security.FieldModeResponse;
import org.jspecify.annotations.Nullable;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Everything an account may use now: its roles, the console resources and API permissions they give, the rows its data
 * policies allow and deny, and the fields it does not see or change freely.
 *
 * @param accountId the account
 * @param roles every role it has, active or not, with whom each is assigned to
 * @param resources the console resources it may use, in catalog order
 * @param permissions the API permissions it holds, by code
 * @param data the data rules by entity and action
 * @param fields the restricted fields, by entity and field code such as {@code user.email}
 */
public record EffectiveAccessResponse(String accountId, List<Role> roles, List<Item> resources, List<Item> permissions, List<Data> data,
        Map<String, FieldModeResponse> fields)
{
    /** Copies the collections. */
    public EffectiveAccessResponse
    {
        roles = List.copyOf(roles);
        resources = List.copyOf(resources);
        permissions = List.copyOf(permissions);
        data = List.copyOf(data);
        fields = Map.copyOf(fields);
    }

    /**
     * Puts together an account's access.
     *
     * @param accountId the account
     * @param access its roles, resources and permissions
     * @param data what its roles say about data
     * @param fields its restricted fields
     * @return the response
     */
    public static EffectiveAccessResponse from(long accountId, EffectiveAccess access, DataAccess data, Map<String, FieldMode> fields)
    {
        return new EffectiveAccessResponse(Long.toString(accountId), access.roles().stream().map(Role::from).toList(),
                access.resources().stream().map(Item::from).toList(), access.permissions().stream().map(Item::from).toList(),
                data.rules().entrySet().stream().map(entry -> new Data(entry.getKey().entityCode(), entry.getKey().action(),
                        entry.getValue().allow().stream().map(Rule::from).toList(), entry.getValue().deny().stream().map(Rule::from).toList()))
                        .sorted(Comparator.comparing(Data::entityCode).thenComparing(Data::action)).toList(),
                fields.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, entry -> FieldModeResponse.from(entry.getValue()))));
    }

    /**
     * A role of the account.
     *
     * @param id its ID
     * @param code its code
     * @param name its name
     * @param active whether it gives anything now: enabled, with an assignment that applies now
     * @param assignedTo whom its assignments are to: the account, or its groups, departments or positions
     */
    public record Role(String id, String code, String name, boolean active, List<AccessExplanationResponse.Holder> assignedTo)
    {
        /** Copies the list. */
        public Role
        {
            assignedTo = List.copyOf(assignedTo);
        }

        static Role from(EffectiveRole role)
        {
            return new Role(Long.toString(role.role().id()), role.role().code(), role.role().name(), role.active(),
                    role.sources().stream().map(source -> AccessExplanationResponse.Holder.from(source.subject())).distinct().toList());
        }
    }

    /**
     * A console resource or API permission the account may use.
     *
     * @param code its code
     * @param name its name
     * @param nameKey the console's message key of the name, if any
     * @param type its type
     * @param parentCode the code of the resource above it, if any
     */
    public record Item(String code, String name, @Nullable String nameKey, ResourceType type, @Nullable String parentCode)
    {
        static Item from(EffectiveAccess.Item item)
        {
            return new Item(item.code(), item.name(), item.nameKey(), item.type(), item.parentCode());
        }
    }

    /**
     * The data rules of an entity and action.
     *
     * @param entityCode the entity
     * @param action the action
     * @param allow the scopes whose rows the account may use
     * @param deny the scopes whose rows it may not use, whatever allows them
     */
    public record Data(String entityCode, DataAction action, List<Rule> allow, List<Rule> deny)
    {
        /** Copies the lists. */
        public Data
        {
            allow = List.copyOf(allow);
            deny = List.copyOf(deny);
        }
    }

    /**
     * One scope.
     *
     * @param scope which rows
     * @param conditional whether a condition picks them
     * @param orgUnitCount how many chosen departments, for the chosen-departments scope
     */
    public record Rule(DataScope scope, boolean conditional, int orgUnitCount)
    {
        static Rule from(DataRule rule)
        {
            return new Rule(rule.scope(), rule.condition() != null, rule.orgUnitIds().size());
        }
    }
}

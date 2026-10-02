// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.service;

import org.devlive.grantforge.plugin.api.model.AccessTypeDefinition;
import org.devlive.grantforge.plugin.api.model.ConditionDefinition;
import org.devlive.grantforge.plugin.api.model.ConfigField;
import org.devlive.grantforge.plugin.api.model.ConfigFieldType;
import org.devlive.grantforge.plugin.api.model.DataMaskDefinition;
import org.devlive.grantforge.plugin.api.model.MaskTypeDefinition;
import org.devlive.grantforge.plugin.api.model.MatcherType;
import org.devlive.grantforge.plugin.api.model.PolicyType;
import org.devlive.grantforge.plugin.api.model.ResourceDefinition;
import org.devlive.grantforge.plugin.api.model.RowFilterDefinition;
import org.devlive.grantforge.plugin.api.model.ServiceTypeDefinition;
import org.jspecify.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * A service type an active plugin provides: what the form for a service of it asks, and what its policies can say.
 *
 * @param name the type's name
 * @param label what the console shows
 * @param description a longer explanation
 * @param configFields the settings of a service, in form order
 * @param resources the resource levels
 * @param accessTypes the access types, in the order the console lists them
 * @param policyTypes the kinds of policies the type supports
 * @param maskTypes the masking methods, if the type masks data
 * @param maskableResources the levels masking policies may end at
 * @param filterableResources the levels row filtering policies may end at
 * @param conditions the conditions policy items may carry
 */
public record ServiceTypeResponse(String name, String label, @Nullable String description, List<Field> configFields,
        List<Level> resources, List<Access> accessTypes, List<PolicyType> policyTypes, List<Mask> maskTypes,
        List<String> maskableResources, List<String> filterableResources, List<Condition> conditions)
{
    /** Copies the lists. */
    public ServiceTypeResponse
    {
        configFields = List.copyOf(configFields);
        resources = List.copyOf(resources);
        accessTypes = List.copyOf(accessTypes);
        policyTypes = List.copyOf(policyTypes);
        maskTypes = List.copyOf(maskTypes);
        maskableResources = List.copyOf(maskableResources);
        filterableResources = List.copyOf(filterableResources);
        conditions = List.copyOf(conditions);
    }

    /**
     * One setting.
     *
     * @param name its name
     * @param label what the console shows
     * @param type the kind of value
     * @param mandatory whether a service must set it
     * @param defaultValue the value used when none is given
     * @param options the allowed values of a choice
     * @param pattern the format a text value must have
     * @param description help text
     */
    public record Field(String name, String label, ConfigFieldType type, boolean mandatory, @Nullable String defaultValue,
            List<String> options, @Nullable String pattern, @Nullable String description)
    {
        /** Copies the options. */
        public Field
        {
            options = List.copyOf(options);
        }

        static Field from(ConfigField field)
        {
            return new Field(field.name(), field.label(), field.type(), field.mandatory(), field.defaultValue(), field.options(),
                    field.pattern(), field.description());
        }
    }

    /**
     * One resource level.
     *
     * @param name its name
     * @param label what the console shows
     * @param parent the level above
     * @param lookupSupported whether existing values can be looked up
     * @param matcher how policy values are compared with requested resources
     * @param excludesSupported whether a policy may cover everything except its values
     * @param recursiveSupported whether a value may also cover everything below it
     * @param validLeaf whether a policy may end here although there are levels below
     * @param accessTypes the access types that apply here, sorted; empty for all of the type's
     */
    public record Level(String name, String label, @Nullable String parent, boolean lookupSupported, MatcherType matcher,
            boolean excludesSupported, boolean recursiveSupported, boolean validLeaf, List<String> accessTypes)
    {
        /** Copies the access types. */
        public Level
        {
            accessTypes = List.copyOf(accessTypes);
        }

        static Level from(ResourceDefinition level)
        {
            return new Level(level.name(), level.label(), level.parent(), level.lookupSupported(), level.matcher(),
                    level.excludesSupported(), level.recursiveSupported(), level.validLeaf(), sorted(level.accessTypes()));
        }
    }

    /**
     * One access type.
     *
     * @param name its name
     * @param label what the console shows
     * @param impliedGrants the access types it includes, sorted
     */
    public record Access(String name, String label, List<String> impliedGrants)
    {
        /** Copies the implied grants. */
        public Access
        {
            impliedGrants = List.copyOf(impliedGrants);
        }

        static Access from(AccessTypeDefinition access)
        {
            return new Access(access.name(), access.label(), sorted(access.impliedGrants()));
        }
    }

    /**
     * One masking method.
     *
     * @param name its name
     * @param label what the console shows
     * @param transformer the expression the target system applies, or {@code null} if the policy item gives one
     */
    public record Mask(String name, String label, @Nullable String transformer)
    {
        static Mask from(MaskTypeDefinition mask)
        {
            return new Mask(mask.name(), mask.label(), mask.transformer());
        }
    }

    /**
     * One condition policy items may carry.
     *
     * @param name its name
     * @param label what the console shows
     */
    public record Condition(String name, String label)
    {
        static Condition from(ConditionDefinition condition)
        {
            return new Condition(condition.name(), condition.label());
        }
    }

    /**
     * Converts a definition.
     *
     * @param definition the definition
     * @return the response
     */
    public static ServiceTypeResponse from(ServiceTypeDefinition definition)
    {
        Optional<DataMaskDefinition> masking = Optional.ofNullable(definition.dataMask());
        return new ServiceTypeResponse(definition.name(), definition.label(), definition.description(),
                definition.configFields().stream().map(Field::from).toList(), definition.resources().stream().map(Level::from).toList(),
                definition.accessTypes().stream().map(Access::from).toList(), definition.policyTypes().stream().sorted().toList(),
                masking.map(DataMaskDefinition::maskTypes).orElse(List.of()).stream().map(Mask::from).toList(),
                sorted(masking.map(DataMaskDefinition::resources).orElse(Set.of())),
                sorted(Optional.ofNullable(definition.rowFilter()).map(RowFilterDefinition::resources).orElse(Set.of())),
                definition.conditions().stream().map(Condition::from).toList());
    }

    private static List<String> sorted(Collection<String> names)
    {
        return names.stream().sorted().toList();
    }
}

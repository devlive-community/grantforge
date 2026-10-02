// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.service;

import org.devlive.grantforge.plugin.api.model.ConfigField;
import org.devlive.grantforge.plugin.api.model.ConfigFieldType;
import org.devlive.grantforge.plugin.api.model.ResourceDefinition;
import org.devlive.grantforge.plugin.api.model.ServiceTypeDefinition;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * A service type an active plugin provides: what the form for a service of it asks, and its resource levels.
 *
 * @param name the type's name
 * @param label what the console shows
 * @param description a longer explanation
 * @param configFields the settings of a service, in form order
 * @param resources the resource levels
 */
public record ServiceTypeResponse(String name, String label, @Nullable String description, List<Field> configFields,
        List<Level> resources)
{
    /** Copies the lists. */
    public ServiceTypeResponse
    {
        configFields = List.copyOf(configFields);
        resources = List.copyOf(resources);
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
     */
    public record Level(String name, String label, @Nullable String parent, boolean lookupSupported)
    {
        static Level from(ResourceDefinition level)
        {
            return new Level(level.name(), level.label(), level.parent(), level.lookupSupported());
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
        return new ServiceTypeResponse(definition.name(), definition.label(), definition.description(),
                definition.configFields().stream().map(Field::from).toList(), definition.resources().stream().map(Level::from).toList());
    }
}

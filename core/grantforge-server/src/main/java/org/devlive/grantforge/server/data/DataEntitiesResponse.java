// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.data;

import org.devlive.grantforge.authz.data.ComparisonOperator;
import org.devlive.grantforge.authz.data.ConditionVariable;
import org.devlive.grantforge.persistence.secured.DataField;
import org.devlive.grantforge.persistence.secured.DataFieldType;
import org.devlive.grantforge.persistence.secured.DataScope;
import org.devlive.grantforge.persistence.secured.SecuredEntityDefinition;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/**
 * What data policies can say: the secured entities with their scopes and filterable fields, and the variables conditions
 * may use.
 *
 * @param entities the entities, by code
 * @param variables the variables
 */
public record DataEntitiesResponse(List<Entity> entities, List<Variable> variables)
{
    /** Copies the lists. */
    public DataEntitiesResponse
    {
        entities = List.copyOf(entities);
        variables = List.copyOf(variables);
    }

    /**
     * Describes entities and every variable.
     *
     * @param definitions the entities
     * @return the response
     */
    public static DataEntitiesResponse from(Collection<SecuredEntityDefinition> definitions)
    {
        return new DataEntitiesResponse(definitions.stream().map(Entity::from).toList(),
                Arrays.stream(ConditionVariable.values()).map(variable -> new Variable(variable.key(), variable.type(), variable.list()))
                        .toList());
    }

    /**
     * A secured entity.
     *
     * @param code its code
     * @param name what its rows are
     * @param scopes the scopes its policies may use
     * @param fields the fields conditions may test
     */
    public record Entity(String code, String name, List<DataScope> scopes, List<Field> fields)
    {
        /** Copies the lists. */
        public Entity
        {
            scopes = List.copyOf(scopes);
            fields = List.copyOf(fields);
        }

        static Entity from(SecuredEntityDefinition definition)
        {
            return new Entity(definition.code(), definition.name(), definition.scopes().stream().sorted().toList(),
                    definition.fields().stream().map(Field::from).toList());
        }
    }

    /**
     * A field conditions may test.
     *
     * @param code its attribute
     * @param name what it is
     * @param type its kind of value
     * @param choices the values of a choice field
     * @param operators the comparisons it allows, as conditions name them
     */
    public record Field(String code, String name, DataFieldType type, List<String> choices, List<String> operators)
    {
        /** Copies the lists. */
        public Field
        {
            choices = List.copyOf(choices);
            operators = List.copyOf(operators);
        }

        static Field from(DataField field)
        {
            return new Field(field.code(), field.name(), field.type(), field.choices(), Arrays.stream(ComparisonOperator.values())
                    .filter(operator -> operator.appliesTo(field.type())).map(ComparisonOperator::symbol).toList());
        }
    }

    /**
     * A variable conditions may use.
     *
     * @param key its name, as in {@code {"var": "subject.id"}}
     * @param type its kind of value
     * @param list whether it holds several values, for {@code in} and {@code not_in}
     */
    public record Variable(String key, DataFieldType type, boolean list)
    {
    }
}

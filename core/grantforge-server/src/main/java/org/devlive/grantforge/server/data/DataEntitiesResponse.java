// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.data;

import org.devlive.grantforge.authz.data.ComparisonOperator;
import org.devlive.grantforge.authz.data.ConditionVariable;
import org.devlive.grantforge.authz.data.DataEntities;
import org.devlive.grantforge.persistence.secured.DataField;
import org.devlive.grantforge.persistence.secured.DataFieldType;
import org.devlive.grantforge.persistence.secured.DataScope;
import org.devlive.grantforge.persistence.secured.DeclaredField;
import org.devlive.grantforge.persistence.secured.SecuredEntityDefinition;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * What data and field policies can say: the secured entities with their scopes, filterable fields and secured fields, and
 * the variables conditions may use.
 *
 * @param entities the entities, by code
 * @param variables the variables
 */
public record DataEntitiesResponse(List<DataEntity> entities, List<DataVariable> variables)
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
     * @param securedFields the secured fields of each entity, by entity code
     * @return the response
     */
    public static DataEntitiesResponse from(Collection<SecuredEntityDefinition> definitions, Map<String, List<DeclaredField>> securedFields)
    {
        return new DataEntitiesResponse(definitions.stream().map(definition -> DataEntity.from(definition,
                        securedFields.getOrDefault(definition.code(), List.of()))).toList(),
                Arrays.stream(ConditionVariable.values()).map(variable -> new DataVariable(variable.key(), variable.type(), variable.list()))
                        .toList());
    }

    /**
     * A secured entity.
     *
     * @param code its code
     * @param name what its rows are
     * @param scopes the scopes its policies may use
     * @param fields the fields conditions may test
     * @param securedFields the fields APIs return or accept that field policies may hide, mask or lock
     * @param previewable whether GrantForge can count its rows, as only for the console's own entities
     */
    public record DataEntity(String code, String name, List<DataScope> scopes, List<DataEntityField> fields,
            List<DataSecuredField> securedFields, boolean previewable)
    {
        /** Copies the lists. */
        public DataEntity
        {
            scopes = List.copyOf(scopes);
            fields = List.copyOf(fields);
            securedFields = List.copyOf(securedFields);
        }

        static DataEntity from(SecuredEntityDefinition definition, List<DeclaredField> secured)
        {
            return new DataEntity(definition.code(), definition.name(), definition.scopes().stream().sorted().toList(),
                    definition.fields().stream().map(DataEntityField::from).toList(),
                    secured.stream().map(field -> new DataSecuredField(field.field(), field.name())).toList(),
                    !DataEntities.ofAnApplication(definition));
        }
    }

    /**
     * A field field policies may hide, mask or lock.
     *
     * @param code its code within the entity
     * @param name what it is
     */
    public record DataSecuredField(String code, String name)
    {
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
    public record DataEntityField(String code, String name, DataFieldType type, List<String> choices, List<String> operators)
    {
        /** Copies the lists. */
        public DataEntityField
        {
            choices = List.copyOf(choices);
            operators = List.copyOf(operators);
        }

        static DataEntityField from(DataField field)
        {
            return new DataEntityField(field.code(), field.name(), field.type(), field.choices(), Arrays.stream(ComparisonOperator.values())
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
    public record DataVariable(String key, DataFieldType type, boolean list)
    {
    }
}

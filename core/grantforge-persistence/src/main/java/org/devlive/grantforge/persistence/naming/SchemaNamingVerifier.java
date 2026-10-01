// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.persistence.naming;

import jakarta.persistence.EntityManagerFactory;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.metamodel.mapping.SelectableConsumer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import static java.util.Objects.requireNonNull;

/**
 * Applies {@link SchemaNamingRules} to every table and column mapped in a persistence unit.
 *
 * <p>Modules with entities call {@link #verify(EntityManagerFactory)} from a JPA slice test so that a
 * non-portable name fails the build before it reaches a Liquibase changeset.
 */
public final class SchemaNamingVerifier
{
    private SchemaNamingVerifier()
    {
    }

    /**
     * Returns all naming violations of the mapped tables and columns.
     *
     * @param entityManagerFactory a Hibernate-backed factory
     * @return violations sorted by identifier; empty when every name is valid
     */
    public static List<String> verify(EntityManagerFactory entityManagerFactory)
    {
        MappedNames names = mappedNames(entityManagerFactory);
        List<String> violations = new ArrayList<>();
        for (String table : names.tables()) {
            violations.addAll(SchemaNamingRules.violations(SchemaNamingRules.Kind.TABLE, table));
        }
        for (String column : names.columns()) {
            violations.addAll(SchemaNamingRules.violations(SchemaNamingRules.Kind.COLUMN, column));
        }
        return violations;
    }

    /**
     * Collects the table and column names Hibernate maps, including ID and version columns.
     *
     * @param entityManagerFactory a Hibernate-backed factory
     * @return the sorted, de-duplicated names
     */
    public static MappedNames mappedNames(EntityManagerFactory entityManagerFactory)
    {
        SessionFactoryImplementor sessionFactory =
                requireNonNull(entityManagerFactory, "entityManagerFactory").unwrap(SessionFactoryImplementor.class);
        Set<String> tables = new TreeSet<>();
        Set<String> columns = new TreeSet<>();
        SelectableConsumer collect = (index, selectable) -> {
            tables.add(selectable.getContainingTableExpression());
            // Formula-based attributes have no physical column to check.
            if (!selectable.isFormula()) {
                columns.add(selectable.getSelectionExpression());
            }
        };
        sessionFactory.getMappingMetamodel().forEachEntityDescriptor(entity -> {
            // forEachSelectable skips the identifier, so the ID columns are visited separately.
            entity.getIdentifierMapping().forEachSelectable(collect);
            entity.forEachSelectable(collect);
        });
        return new MappedNames(tables, columns);
    }

    /**
     * Physical names mapped by a persistence unit.
     *
     * @param tables table names
     * @param columns column names
     */
    public record MappedNames(Set<String> tables, Set<String> columns)
    {
        /** Copies the sets into sorted, immutable views. */
        public MappedNames
        {
            tables = Collections.unmodifiableSortedSet(new TreeSet<>(requireNonNull(tables, "tables")));
            columns = Collections.unmodifiableSortedSet(new TreeSet<>(requireNonNull(columns, "columns")));
        }
    }
}

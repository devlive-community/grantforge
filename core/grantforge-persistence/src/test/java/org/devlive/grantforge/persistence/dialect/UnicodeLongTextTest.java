// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.persistence.dialect;

import org.devlive.grantforge.fixture.longtext.LongTextEntity;
import org.hibernate.Length;
import org.hibernate.boot.Metadata;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.model.TypeContributor;
import org.hibernate.boot.registry.StandardServiceRegistry;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.dialect.Dialect;
import org.hibernate.dialect.H2Dialect;
import org.hibernate.dialect.PostgreSQLDialect;
import org.hibernate.dialect.SQLServerDialect;
import org.hibernate.engine.jdbc.Size;
import org.hibernate.mapping.Column;
import org.hibernate.type.SqlTypes;
import org.hibernate.type.spi.TypeConfiguration;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.ServiceLoader;

import static org.assertj.core.api.Assertions.assertThat;

class UnicodeLongTextTest
{
    @Test
    void sqlServerExpectsUnicodeLongText()
    {
        TypeConfiguration types = new TypeConfiguration();
        UnicodeLongText.register(types.getDdlTypeRegistry(), new SQLServerDialect());

        assertThat(types.getDdlTypeRegistry().getTypeName(SqlTypes.LONG32VARCHAR, Size.length(Length.LONG32)))
                .isEqualTo(UnicodeLongText.NVARCHAR_MAX);
        assertThat(types.getDdlTypeRegistry().getTypeName(SqlTypes.CLOB, Size.length(Length.LONG32))).isEqualTo(UnicodeLongText.NVARCHAR_MAX);
    }

    @Test
    void otherDatabasesKeepHibernateTypes()
    {
        TypeConfiguration types = new TypeConfiguration();
        UnicodeLongText.register(types.getDdlTypeRegistry(), new PostgreSQLDialect());
        UnicodeLongText.register(types.getDdlTypeRegistry(), new H2Dialect());

        assertThat(types.getDdlTypeRegistry().getDescriptor(SqlTypes.LONG32VARCHAR)).isNull();
        assertThat(types.getDdlTypeRegistry().getDescriptor(SqlTypes.CLOB)).isNull();
    }

    @Test
    void hibernateFindsTheContributor()
    {
        assertThat(ServiceLoader.load(TypeContributor.class)).anyMatch(UnicodeLongText.class::isInstance);
    }

    @Test
    void sqlServerColumnsOfLongTextAreUnicode()
    {
        assertThat(columnType(SQLServerDialect.class)).isEqualTo(UnicodeLongText.NVARCHAR_MAX);
        assertThat(columnType(PostgreSQLDialect.class)).isEqualTo("text");
    }

    private static String columnType(Class<? extends Dialect> dialect)
    {
        StandardServiceRegistry registry = new StandardServiceRegistryBuilder()
                .applySettings(Map.of("hibernate.dialect", dialect.getName(), "hibernate.boot.allow_jdbc_metadata_access", "false"))
                .build();
        try {
            Metadata metadata = new MetadataSources(registry).addAnnotatedClass(LongTextEntity.class).buildMetadata();
            Column column = metadata.getEntityBinding(LongTextEntity.class.getName()).getProperty("body").getColumns().get(0);
            return column.getSqlType(metadata);
        }
        finally {
            StandardServiceRegistryBuilder.destroy(registry);
        }
    }
}

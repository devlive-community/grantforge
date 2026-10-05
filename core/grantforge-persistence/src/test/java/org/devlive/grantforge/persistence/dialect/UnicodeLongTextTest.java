// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.persistence.dialect;

import org.hibernate.Length;
import org.hibernate.boot.model.TypeContributor;
import org.hibernate.dialect.H2Dialect;
import org.hibernate.dialect.PostgreSQLDialect;
import org.hibernate.dialect.SQLServerDialect;
import org.hibernate.engine.jdbc.Size;
import org.hibernate.type.SqlTypes;
import org.hibernate.type.spi.TypeConfiguration;
import org.junit.jupiter.api.Test;

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
    }

    @Test
    void otherDatabasesKeepHibernateTypes()
    {
        TypeConfiguration types = new TypeConfiguration();
        UnicodeLongText.register(types.getDdlTypeRegistry(), new PostgreSQLDialect());
        UnicodeLongText.register(types.getDdlTypeRegistry(), new H2Dialect());

        assertThat(types.getDdlTypeRegistry().getDescriptor(SqlTypes.LONG32VARCHAR)).isNull();
    }

    @Test
    void hibernateFindsTheContributor()
    {
        assertThat(ServiceLoader.load(TypeContributor.class)).anyMatch(UnicodeLongText.class::isInstance);
    }
}

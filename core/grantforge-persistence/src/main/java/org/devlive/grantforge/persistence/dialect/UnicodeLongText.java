// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.persistence.dialect;

import org.hibernate.boot.model.TypeContributions;
import org.hibernate.boot.model.TypeContributor;
import org.hibernate.dialect.Dialect;
import org.hibernate.dialect.SQLServerDialect;
import org.hibernate.engine.jdbc.spi.JdbcServices;
import org.hibernate.service.ServiceRegistry;
import org.hibernate.type.SqlTypes;
import org.hibernate.type.descriptor.sql.internal.DdlTypeImpl;
import org.hibernate.type.descriptor.sql.spi.DdlTypeRegistry;

/**
 * Makes Hibernate expect long text ({@code SqlTypes.LONG32VARCHAR}) on SQL Server as {@code nvarchar(max)}, which the
 * changelogs create ({@code ${longtext}}), rather than {@code varchar(max)}, which is not Unicode there. SQL Server stores
 * long text as a LOB, so Hibernate maps it to the CLOB column type, which changes too; no entity maps a CLOB otherwise.
 * Other databases keep Hibernate's types. Registered through {@code META-INF/services}.
 */
public final class UnicodeLongText
        implements TypeContributor
{
    /** SQL Server's Unicode text without a length limit. */
    static final String NVARCHAR_MAX = "nvarchar(max)";

    @Override
    public void contribute(TypeContributions contributions, ServiceRegistry services)
    {
        register(contributions.getTypeConfiguration().getDdlTypeRegistry(), services.requireService(JdbcServices.class).getDialect());
    }

    /**
     * Replaces the long text type when the database is SQL Server.
     *
     * @param types the column types
     * @param dialect the database
     */
    static void register(DdlTypeRegistry types, Dialect dialect)
    {
        if (dialect instanceof SQLServerDialect) {
            types.addDescriptor(new DdlTypeImpl(SqlTypes.LONG32VARCHAR, NVARCHAR_MAX, dialect));
            types.addDescriptor(new DdlTypeImpl(SqlTypes.CLOB, NVARCHAR_MAX, dialect));
        }
    }
}

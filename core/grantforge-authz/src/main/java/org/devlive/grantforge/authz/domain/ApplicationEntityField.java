// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.authz.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import org.devlive.grantforge.persistence.secured.DataField;
import org.devlive.grantforge.persistence.secured.DataFieldType;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.jspecify.annotations.Nullable;

import java.util.Arrays;
import java.util.List;

import static java.util.Objects.requireNonNull;

/** A field of an application's data entity that conditions may test. */
@Embeddable
public class ApplicationEntityField
{
    /** Separates choices in their column; no choice contains it. */
    public static final String CHOICE_SEPARATOR = "\n";

    @Column(name = "code", nullable = false, length = 64)
    private String code = "";

    @Column(name = "name", nullable = false, length = 128)
    private String name = "";

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "field_type", nullable = false, length = 16)
    private DataFieldType type = DataFieldType.TEXT;

    @Column(name = "choices", length = 1000)
    private @Nullable String choices;

    /** For JPA. */
    protected ApplicationEntityField()
    {
    }

    /**
     * Describes a field.
     *
     * @param field the field, as conditions see it
     * @return the stored description
     */
    public static ApplicationEntityField of(DataField field)
    {
        ApplicationEntityField described = new ApplicationEntityField();
        described.code = requireNonNull(field.code(), "code");
        described.name = requireNonNull(field.name(), "name");
        described.type = requireNonNull(field.type(), "type");
        // Without fixed choices the column stays NULL.
        if (!field.choices().isEmpty()) {
            described.choices = String.join(CHOICE_SEPARATOR, field.choices());
        }
        return described;
    }

    /**
     * Returns the field as conditions see it.
     *
     * @return the field
     */
    public DataField toField()
    {
        String stored = choices;
        return new DataField(code, name, type, stored == null ? List.of() : Arrays.asList(stored.split(CHOICE_SEPARATOR)));
    }
}

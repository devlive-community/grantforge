// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.authz.domain;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import org.devlive.grantforge.persistence.authz.AuthorizationChangeListener;
import org.devlive.grantforge.persistence.entity.BaseEntity;
import org.devlive.grantforge.persistence.secured.DataField;

import java.util.ArrayList;
import java.util.List;

import static java.util.Objects.requireNonNull;

/**
 * A data entity of an application other than the console, as the application declares it: rows live in the application,
 * GrantForge only knows what data policies may say about them. Its code is the application's code, a colon and the
 * entity's own code. Changing one raises the catalog's authorization version, so worked-out data access is renewed.
 */
@Entity
@Table(name = "gf_app_entity")
@EntityListeners(AuthorizationChangeListener.class)
public class ApplicationEntity
        extends BaseEntity
{
    /** Separates the application's code from the entity's own in codes. */
    public static final String SEPARATOR = ":";

    @Column(name = "application_id", nullable = false, updatable = false)
    private long applicationId;

    @Column(name = "code", nullable = false, updatable = false, length = 64)
    private String code = "";

    @Column(name = "name", nullable = false, length = 128)
    private String name = "";

    @Column(name = "owned", nullable = false)
    private boolean owned;

    @Column(name = "unit_based", nullable = false)
    private boolean unitBased;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "gf_app_entity_field", joinColumns = @JoinColumn(name = "app_entity_id"))
    @OrderColumn(name = "sort_order")
    private List<ApplicationEntityField> fields = new ArrayList<>();

    /** For JPA. */
    protected ApplicationEntity()
    {
    }

    /**
     * Declares an entity.
     *
     * @param applicationId its application
     * @param code its full code
     * @return the entity, without description yet
     */
    public static ApplicationEntity create(long applicationId, String code)
    {
        ApplicationEntity entity = new ApplicationEntity();
        entity.applicationId = applicationId;
        entity.code = requireNonNull(code, "code");
        return entity;
    }

    /**
     * Describes the entity.
     *
     * @param newName what its rows are
     * @param hasOwner whether rows belong to an account
     * @param hasUnit whether rows belong to a department
     * @param newFields the fields conditions may test, in order
     */
    public void describe(String newName, boolean hasOwner, boolean hasUnit, List<DataField> newFields)
    {
        this.name = requireNonNull(newName, "newName");
        this.owned = hasOwner;
        this.unitBased = hasUnit;
        List<ApplicationEntityField> described = newFields.stream().map(ApplicationEntityField::new).toList();
        if (!described.stream().map(ApplicationEntityField::toField).toList().equals(fields.stream().map(ApplicationEntityField::toField)
                .toList())) {
            fields.clear();
            fields.addAll(described);
        }
    }

    /**
     * Returns the application.
     *
     * @return the application's ID
     */
    public long getApplicationId()
    {
        return applicationId;
    }

    /**
     * Returns the full code.
     *
     * @return the application's code, a colon and the entity's own code
     */
    public String getCode()
    {
        return code;
    }

    /**
     * Returns what the rows are.
     *
     * @return the name
     */
    public String getName()
    {
        return name;
    }

    /**
     * Returns whether rows belong to an account.
     *
     * @return {@code true} if "own rows" applies
     */
    public boolean isOwned()
    {
        return owned;
    }

    /**
     * Returns whether rows belong to a department.
     *
     * @return {@code true} if department scopes apply
     */
    public boolean isUnitBased()
    {
        return unitBased;
    }

    /**
     * Returns the fields conditions may test.
     *
     * @return the fields, in order
     */
    public List<DataField> getFields()
    {
        return fields.stream().map(ApplicationEntityField::toField).toList();
    }
}

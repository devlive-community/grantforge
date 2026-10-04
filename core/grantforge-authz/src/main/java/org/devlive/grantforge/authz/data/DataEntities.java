// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.authz.data;

import org.devlive.grantforge.authz.domain.ApplicationEntity;
import org.devlive.grantforge.authz.domain.ApplicationEntityRepository;
import org.devlive.grantforge.persistence.secured.SecuredEntities;
import org.devlive.grantforge.persistence.secured.SecuredEntityDefinition;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static java.util.Objects.requireNonNull;

/**
 * Every entity data policies may name: the console's own secured entities, and the entities applications declare. An
 * application's entity is described like a secured entity whose type is {@link ApplicationRows}: its rows live in the
 * application, which applies the rules itself, so GrantForge knows its fields and whether rows have an owner or a
 * department, not its columns.
 */
@Service
public final class DataEntities
{
    private static final String OWNER = "owner";
    private static final String UNIT = "unit";

    private final SecuredEntities console;
    private final ApplicationEntityRepository declared;

    /**
     * Creates the directory.
     *
     * @param console the console's secured entities
     * @param declared the entities applications declared
     */
    public DataEntities(SecuredEntities console, ApplicationEntityRepository declared)
    {
        this.console = requireNonNull(console, "console");
        this.declared = requireNonNull(declared, "declared");
    }

    /**
     * Finds an entity.
     *
     * @param code its code: a secured entity's, or an application's code, a colon and the entity's
     * @return the entity
     */
    public Optional<SecuredEntityDefinition> find(String code)
    {
        Optional<SecuredEntityDefinition> own = console.find(code);
        if (own.isPresent() || !code.contains(ApplicationEntity.SEPARATOR)) {
            return own;
        }
        return declared.findByCode(code).map(DataEntities::definition);
    }

    /**
     * Returns the console's own secured entities, those system roles imply rights to.
     *
     * @return the entities
     */
    public List<SecuredEntityDefinition> console()
    {
        return console.all();
    }

    /**
     * Returns every entity by code, the console's first.
     *
     * @return the entities
     */
    public Map<String, SecuredEntityDefinition> all()
    {
        Map<String, SecuredEntityDefinition> all = new LinkedHashMap<>();
        console.all().forEach(entity -> all.put(entity.code(), entity));
        declared.findAllByOrderByCode().forEach(entity -> all.put(entity.getCode(), definition(entity)));
        return all;
    }

    /**
     * Returns the entities an application declared.
     *
     * @param applicationId the application
     * @return the entities, by code
     */
    public List<SecuredEntityDefinition> ofApplication(long applicationId)
    {
        return new ArrayList<>(declared.findByApplicationIdOrderByCode(applicationId).stream().map(DataEntities::definition).toList());
    }

    /**
     * Returns whether an entity is an application's, whose rows GrantForge cannot count.
     *
     * @param entity the entity
     * @return {@code true} for an application's entity
     */
    public static boolean ofAnApplication(SecuredEntityDefinition entity)
    {
        return entity.type() == ApplicationRows.class;
    }

    private static SecuredEntityDefinition definition(ApplicationEntity entity)
    {
        return new SecuredEntityDefinition(entity.getCode(), entity.getName(), ApplicationRows.class, entity.getFields(),
                entity.isOwned() ? OWNER : null, entity.isUnitBased() ? UNIT : null, false, false, null);
    }

    /** Stands for the rows of an application's entity, which live in the application. */
    public static final class ApplicationRows
    {
        private ApplicationRows()
        {
        }
    }
}

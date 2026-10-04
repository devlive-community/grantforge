// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.authz.application;

import org.devlive.grantforge.audit.application.AuditLog;
import org.devlive.grantforge.audit.application.AuditRecord;
import org.devlive.grantforge.audit.domain.AuditAction;
import org.devlive.grantforge.audit.domain.AuditOutcome;
import org.devlive.grantforge.authz.domain.Application;
import org.devlive.grantforge.authz.domain.ApplicationEntityRepository;
import org.devlive.grantforge.authz.domain.ApplicationRepository;
import org.devlive.grantforge.authz.domain.OAuthClientRepository;
import org.devlive.grantforge.authz.domain.ResourceCount;
import org.devlive.grantforge.authz.domain.ResourceRepository;
import org.devlive.grantforge.common.error.CommonErrorCode;
import org.devlive.grantforge.common.error.GrantForgeException;
import org.devlive.grantforge.persistence.tenant.TenantContext;
import org.jspecify.annotations.Nullable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static java.util.Objects.requireNonNull;

/**
 * The applications of the resource catalog. Administrators read them; platform administrators change them (see
 * {@link CatalogAccess}). Every method must be called with the actor's tenant bound.
 */
@Service
public final class ApplicationService
{
    private final ApplicationRepository applications;
    private final ResourceRepository resources;
    private final OAuthClientRepository clients;
    private final ApplicationEntityRepository entities;
    private final CatalogAccess access;
    private final AuditLog audit;
    private final TransactionTemplate transactions;

    /**
     * Creates the service.
     *
     * @param applications applications
     * @param resources resources, to count them and keep applications with resources from being deleted
     * @param clients OAuth clients, which keep their application from being deleted
     * @param entities the data entities applications declared, which keep them from being deleted too
     * @param access who may read and change the catalog
     * @param audit records every change
     * @param transactionManager opens transactions
     */
    public ApplicationService(ApplicationRepository applications, ResourceRepository resources, OAuthClientRepository clients,
            ApplicationEntityRepository entities, CatalogAccess access, AuditLog audit, PlatformTransactionManager transactionManager)
    {
        this.clients = requireNonNull(clients, "clients");
        this.entities = requireNonNull(entities, "entities");
        this.applications = requireNonNull(applications, "applications");
        this.resources = requireNonNull(resources, "resources");
        this.access = requireNonNull(access, "access");
        this.audit = requireNonNull(audit, "audit");
        this.transactions = new TransactionTemplate(requireNonNull(transactionManager, "transactionManager"));
    }

    /**
     * Lists the applications, built-in ones first, with their resource counts.
     *
     * @param actorId the account asking
     * @return the applications
     */
    public List<ApplicationView> list(long actorId)
    {
        return requireNonNull(transactions.execute(status -> {
            Map<Long, Long> counts = resources.countByApplication().stream()
                    .collect(Collectors.toMap(ResourceCount::applicationId, ResourceCount::resources));
            return applications.findOrdered().stream()
                    .map(application -> ApplicationView.from(application, counts.getOrDefault(application.requireId(), 0L)))
                    .toList();
        }));
    }

    /**
     * Registers an application.
     *
     * @param actorId the account asking
     * @param code the code, unique among applications
     * @param name the name
     * @param description an optional explanation
     * @return the application
     * @throws GrantForgeException with {@link AuthzErrorCode#APPLICATION_CODE_TAKEN} or {@link CommonErrorCode#BAD_REQUEST}
     */
    public ApplicationView create(long actorId, @Nullable String code, @Nullable String name, @Nullable String description)
    {
        Application application = audited(() -> write(actorId, () -> {
            Application created = Catalog.valid(() -> Application.create(String.valueOf(code), String.valueOf(name),
                    description));
            applications.findByCode(created.getCode()).ifPresent(other -> {
                throw new GrantForgeException(AuthzErrorCode.APPLICATION_CODE_TAKEN, "application code taken",
                        created.getCode());
            });
            return applications.saveAndFlush(created);
        }), made -> record(AuditAction.APPLICATION_CREATED, actorId, made));
        return ApplicationView.from(application, 0);
    }

    /**
     * Changes the name and description of an application.
     *
     * @param actorId the account asking
     * @param id the application
     * @param name the new name
     * @param description the new description; blank removes it
     * @return the application
     * @throws GrantForgeException with {@link CommonErrorCode#NOT_FOUND} or
     *         {@link CommonErrorCode#BAD_REQUEST}
     */
    public ApplicationView update(long actorId, long id, @Nullable String name, @Nullable String description)
    {
        Application application = audited(() -> write(actorId, () -> {
            Application found = require(id);
            Catalog.valid(() -> {
                found.describe(String.valueOf(name), description);
                return found;
            });
            return applications.saveAndFlush(found);
        }), made -> record(AuditAction.APPLICATION_UPDATED, actorId, made));
        return ApplicationView.from(application, requireNonNull(transactions.execute(status -> resources.countByApplication()
                .stream().filter(count -> count.applicationId() == id).mapToLong(ResourceCount::resources).sum())));
    }

    /**
     * Deletes an application that is not built in and has no resources and no OAuth clients.
     *
     * @param actorId the account asking
     * @param id the application
     * @throws GrantForgeException with {@link CommonErrorCode#NOT_FOUND},
     *         {@link AuthzErrorCode#APPLICATION_PROTECTED} or {@link AuthzErrorCode#APPLICATION_NOT_EMPTY}
     */
    public void delete(long actorId, long id)
    {
        audited(() -> write(actorId, () -> {
            Application found = require(id);
            if (found.isBuiltin()) {
                throw new GrantForgeException(AuthzErrorCode.APPLICATION_PROTECTED, "application " + id + " is built in");
            }
            if (resources.existsByApplicationId(id) || clients.existsByApplicationId(id) || entities.existsByApplicationId(id)) {
                throw new GrantForgeException(AuthzErrorCode.APPLICATION_NOT_EMPTY, "application " + id + " has resources or clients");
            }
            applications.delete(found);
            return found;
        }), made -> record(AuditAction.APPLICATION_DELETED, actorId, made));
    }

    /**
     * Registers the console's own application unless it exists; called at start-up.
     *
     * @return the console application's ID
     */
    public long registerConsole()
    {
        return requireNonNull(transactions.execute(status -> applications.findByCode(Application.CONSOLE)
                .orElseGet(() -> applications.saveAndFlush(Application.create(Application.CONSOLE, "GrantForge Console",
                        "The pages, buttons and APIs of the GrantForge console").markBuiltin())).requireId()));
    }

    private Application write(long actorId, Supplier<Application> change)
    {
        access.requireEditor(actorId);
        try {
            return requireNonNull(transactions.execute(status -> change.get()));
        }
        catch (DataIntegrityViolationException race) {
            throw new GrantForgeException(CommonErrorCode.CONFLICT, "application changed concurrently", race);
        }
    }

    private Application require(long id)
    {
        return applications.findById(id)
                .orElseThrow(() -> new GrantForgeException(CommonErrorCode.NOT_FOUND, "no application " + id));
    }

    private void record(AuditAction action, long actorId, Application application)
    {
        audit.recordWithChange(new AuditRecord(action, AuditOutcome.SUCCESS, TenantContext.requireTenantId(), actorId, null,
                Long.toString(application.requireId()), application.getCode()));
    }

    /** Makes a change and records its event in one transaction, so neither happens without the other. */
    private <T> T audited(Supplier<T> change, Consumer<T> event)
    {
        return requireNonNull(transactions.execute(status -> {
            T made = change.get();
            event.accept(made);
            return made;
        }));
    }
}

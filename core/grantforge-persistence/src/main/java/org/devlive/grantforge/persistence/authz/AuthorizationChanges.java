// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.persistence.authz;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.devlive.grantforge.persistence.tenant.TenantContext;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.orm.jpa.EntityManagerFactoryUtils;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static java.util.Objects.requireNonNull;

/**
 * Collects, per transaction, the scopes whose permissions a change may affect, and hands them to the
 * {@link AuthorizationVersionSink} just before the transaction commits. Changes outside a transaction are handed
 * over at once. Without a sink (a module used on its own) nothing happens.
 */
public final class AuthorizationChanges
{
    /** The scope of the shared resource catalog: its changes may affect every tenant. */
    public static final String CATALOG = "catalog";

    private final ObjectProvider<AuthorizationVersionSink> sinks;
    private final ObjectProvider<EntityManagerFactory> factories;

    /**
     * Creates the collector.
     *
     * @param sinks the sink, if one is configured
     * @param factories the JPA entity manager factory, flushed before commit so every change is noticed in time
     */
    public AuthorizationChanges(ObjectProvider<AuthorizationVersionSink> sinks, ObjectProvider<EntityManagerFactory> factories)
    {
        this.sinks = requireNonNull(sinks, "sinks");
        this.factories = requireNonNull(factories, "factories");
    }

    /**
     * Returns the scope of one tenant.
     *
     * @param tenantId the tenant
     * @return its scope
     */
    public static String tenantScope(long tenantId)
    {
        return "tenant:" + tenantId;
    }

    /** Notes that permissions of the shared catalog may change. */
    public void catalog()
    {
        mark(CATALOG);
    }

    /**
     * Notes that permissions of a tenant may change.
     *
     * @param tenantId the tenant
     */
    public void tenant(long tenantId)
    {
        mark(tenantScope(tenantId));
    }

    /** Notes that permissions of the bound tenant may change; does nothing when no tenant is bound. */
    public void currentTenant()
    {
        TenantContext.currentTenantId().ifPresent(this::tenant);
    }

    /**
     * Makes sure the transaction notices changes in time: an entity that permissions are worked out from was
     * loaded, or is about to be stored or deleted. Its changes may only be flushed during commit, after the
     * last chance to note them; so before commit, changes are flushed and whatever they note is handed over.
     */
    public void watch()
    {
        pending();
    }

    private void mark(String scope)
    {
        AuthorizationVersionSink sink = sinks.getIfAvailable();
        if (sink == null) {
            return;
        }
        Pending pending = pending();
        if (pending == null) {
            sink.changed(List.of(scope));
            return;
        }
        pending.scopes.add(scope);
    }

    /** The scopes noted in the current transaction, registered on first use; {@code null} without a transaction or sink. */
    private @Nullable Pending pending()
    {
        AuthorizationVersionSink sink = sinks.getIfAvailable();
        if (sink == null || !TransactionSynchronizationManager.isSynchronizationActive()) {
            return null;
        }
        Pending pending = (Pending) TransactionSynchronizationManager.getResource(this);
        if (pending == null) {
            pending = new Pending(sink, factories.getIfAvailable());
            TransactionSynchronizationManager.bindResource(this, pending);
            TransactionSynchronizationManager.registerSynchronization(new Flush(this, pending));
        }
        return pending;
    }

    /** The scopes noted in one transaction. */
    private static final class Pending
    {
        private final AuthorizationVersionSink sink;
        private final @Nullable EntityManagerFactory factory;
        private final Set<String> scopes = new LinkedHashSet<>();

        Pending(AuthorizationVersionSink sink, @Nullable EntityManagerFactory factory)
        {
            this.sink = sink;
            this.factory = factory;
        }
    }

    /** Hands the noted scopes over before commit, and forgets them when the transaction ends. */
    private record Flush(AuthorizationChanges owner, Pending pending)
            implements TransactionSynchronization
    {
        @Override
        // The transaction's own entity manager: closing it is up to the transaction.
        @SuppressWarnings("PMD.CloseResource")
        public void beforeCommit(boolean readOnly)
        {
            // Entity listeners run when changes are flushed, which would otherwise be after this, during commit.
            EntityManager manager = pending.factory == null || readOnly ? null
                    : EntityManagerFactoryUtils.getTransactionalEntityManager(pending.factory);
            if (manager != null) {
                manager.flush();
            }
            if (!pending.scopes.isEmpty()) {
                pending.sink.changed(List.copyOf(pending.scopes));
            }
        }

        @Override
        public void afterCompletion(int status)
        {
            TransactionSynchronizationManager.unbindResourceIfPossible(owner);
        }
    }
}

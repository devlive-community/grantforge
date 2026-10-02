// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.persistence.authz;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.devlive.grantforge.persistence.tenant.TenantContext;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.orm.jpa.EntityManagerHolder;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthorizationChangesTest
{
    private final List<Collection<String>> handed = new ArrayList<>();
    private final AuthorizationVersionSink sink = handed::add;
    private final EntityManagerFactory factory = mock(EntityManagerFactory.class);
    private final EntityManager manager = mock(EntityManager.class);

    @AfterEach
    void endTransaction()
    {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
        if (TransactionSynchronizationManager.hasResource(factory)) {
            TransactionSynchronizationManager.unbindResource(factory);
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> ObjectProvider<T> provider(@Nullable T value)
    {
        ObjectProvider<T> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(value);
        return provider;
    }

    private AuthorizationChanges changes()
    {
        return new AuthorizationChanges(provider(sink), provider(factory));
    }

    @Test
    void withoutASinkNothingHappensAndOutsideATransactionChangesAreHandedOverAtOnce()
    {
        new AuthorizationChanges(AuthorizationChangesTest.<AuthorizationVersionSink>provider(null), provider(factory)).catalog();
        assertThat(handed).isEmpty();

        AuthorizationChanges changes = changes();
        changes.catalog();
        changes.tenant(7);
        changes.watch();
        changes.currentTenant();
        TenantContext.runInTenant(9, changes::currentTenant);
        assertThat(handed).containsExactly(List.of("catalog"), List.of("tenant:7"), List.of("tenant:9"));
    }

    @Test
    void withinATransactionChangesAreFlushedNotedOnceAndHandedOverBeforeCommit()
    {
        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.bindResource(factory, new EntityManagerHolder(manager));
        AuthorizationChanges changes = changes();
        changes.watch();
        changes.tenant(7);
        changes.catalog();
        changes.tenant(7);
        assertThat(handed).isEmpty();

        List<TransactionSynchronization> registered = TransactionSynchronizationManager.getSynchronizations();
        assertThat(registered).hasSize(1);
        registered.get(0).beforeCommit(false);
        verify(manager).flush();
        assertThat(handed).containsExactly(List.of("tenant:7", "catalog"));
        registered.get(0).afterCompletion(TransactionSynchronization.STATUS_COMMITTED);
        assertThat(TransactionSynchronizationManager.hasResource(changes)).isFalse();
    }

    @Test
    void readOnlyTransactionsAreNotFlushedAndWatchingAloneHandsNothingOver()
    {
        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.bindResource(factory, new EntityManagerHolder(manager));
        changes().watch();

        TransactionSynchronizationManager.getSynchronizations().get(0).beforeCommit(true);
        verify(manager, never()).flush();
        assertThat(handed).isEmpty();
    }
}

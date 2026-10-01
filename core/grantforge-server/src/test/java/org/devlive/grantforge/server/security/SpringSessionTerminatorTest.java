// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.core.ResolvableType;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.MapSession;
import org.springframework.session.Session;

import java.util.Map;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class SpringSessionTerminatorTest
{
    @SuppressWarnings("unchecked")
    private static ObjectProvider<FindByIndexNameSessionRepository<? extends Session>> provider(Object... stores)
    {
        StaticListableBeanFactory beans = new StaticListableBeanFactory();
        for (int i = 0; i < stores.length; i++) {
            beans.addBean("store" + i, stores[i]);
        }
        Object found = beans.getBeanProvider(ResolvableType.forClass(FindByIndexNameSessionRepository.class));
        return (ObjectProvider<FindByIndexNameSessionRepository<? extends Session>>) found;
    }

    @Test
    @SuppressWarnings("unchecked")
    void endsOneOrAllSessionsOfAnAccount()
    {
        FindByIndexNameSessionRepository<MapSession> store = mock(FindByIndexNameSessionRepository.class);
        when(store.findByPrincipalName("7")).thenReturn(Map.of("a", new MapSession("a"), "b", new MapSession("b")));
        SpringSessionTerminator terminator = new SpringSessionTerminator(provider(store));

        terminator.terminate("x");
        terminator.terminateAllOf(7);

        verify(store).deleteById("x");
        verify(store).findByPrincipalName("7");
        verify(store).deleteById("a");
        verify(store).deleteById("b");
        verifyNoMoreInteractions(store);
    }

    @Test
    void doesNothingWithoutASessionStore()
    {
        SpringSessionTerminator terminator = new SpringSessionTerminator(provider());

        terminator.terminate("x");
        terminator.terminateAllOf(7);
    }
}

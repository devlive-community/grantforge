// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.authz.application;

import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.dao.DataIntegrityViolationException;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ConsoleApplicationRegistrarTest
{
    @Test
    void registersTheConsoleAndRetriesOnceAfterARace()
    {
        ApplicationService applications = mock(ApplicationService.class);
        when(applications.registerConsole()).thenThrow(new DataIntegrityViolationException("duplicate")).thenReturn(7L);

        new ConsoleApplicationRegistrar(applications).run(new DefaultApplicationArguments());

        verify(applications, times(2)).registerConsole();
    }
}

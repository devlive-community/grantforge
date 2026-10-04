// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.authz.application;

import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AccessRequestSchedulerTest
{
    @Test
    void takesEndedGrantsBack()
    {
        AccessRequestService requests = mock(AccessRequestService.class);
        when(requests.expire()).thenReturn(2, 0);
        AccessRequestScheduler scheduler = new AccessRequestScheduler(requests);

        scheduler.expire();
        scheduler.expire();

        verify(requests, times(2)).expire();
    }
}

// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.identity.application;

import org.devlive.grantforge.common.error.GrantForgeException;
import org.devlive.grantforge.identity.domain.IdentitySource;
import org.devlive.grantforge.identity.domain.IdentitySourceType;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class IdentitySourceSchedulerTest
{
    private static IdentitySource source(long id, long tenant, String code)
    {
        IdentitySource source = IdentitySource.create(code, IdentitySourceType.LDAP);
        ReflectionTestUtils.setField(source, "id", id);
        ReflectionTestUtils.setField(source, "tenantId", tenant);
        return source;
    }

    @Test
    void syncsTheDueSourcesAndGoesOnAfterAFailure()
    {
        IdentitySourceService service = mock(IdentitySourceService.class);
        Instant now = Instant.parse("2026-06-01T09:00:00Z");
        when(service.dueForSync(now)).thenReturn(List.of(source(1, 10, "down"), source(2, 20, "corp")));
        when(service.sync(null, 1)).thenThrow(new GrantForgeException(IdentityErrorCode.IDENTITY_SOURCE_UNAVAILABLE, "down", "down"));
        when(service.sync(null, 2)).thenReturn(new SyncReport(1, 1, 0, 0, List.of()));

        new IdentitySourceScheduler(service, Clock.fixed(now, ZoneOffset.UTC)).syncDue();

        verify(service).sync(null, 1);
        verify(service).sync(null, 2);
        verify(service).dueForSync(any());
    }
}

// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.oauth.application;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OAuthCleanupTest
{
    @Test
    void purgesExpiredAuthorizationsAndRetiredKeys()
    {
        StoredAuthorizations authorizations = mock(StoredAuthorizations.class);
        SigningKeys keys = mock(SigningKeys.class);
        Instant now = Instant.parse("2026-10-04T00:00:00Z");
        when(authorizations.purgeExpired(now)).thenReturn(2);

        new OAuthCleanup(authorizations, keys, Clock.fixed(now, ZoneOffset.UTC)).purge();
        new OAuthCleanup(mock(StoredAuthorizations.class), mock(SigningKeys.class), Clock.fixed(now, ZoneOffset.UTC)).purge();

        verify(authorizations).purgeExpired(now);
        verify(keys).purgeRetired();
    }
}

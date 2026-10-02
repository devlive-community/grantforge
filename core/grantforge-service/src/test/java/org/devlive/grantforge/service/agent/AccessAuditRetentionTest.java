// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.service.agent;

import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class AccessAuditRetentionTest
{
    @Test
    void purgesThroughTheAudit()
    {
        AccessAudit audit = mock(AccessAudit.class);
        new AccessAuditRetention(audit).purge();
        verify(audit).purge();
    }
}

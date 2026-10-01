// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.catalog;

import org.devlive.grantforge.authz.application.ApiCatalogService;
import org.devlive.grantforge.authz.application.DeclaredEndpoint;
import org.devlive.grantforge.authz.application.SyncReport;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ApiCatalogSynchronizerTest
{
    @Test
    void synchronizesWhatTheScannerFinds()
    {
        ApiEndpointScanner scanner = mock(ApiEndpointScanner.class);
        ApiCatalogService catalog = mock(ApiCatalogService.class);
        List<DeclaredEndpoint> found = List.of();
        when(scanner.scan()).thenReturn(found);
        when(catalog.synchronize(found)).thenReturn(new SyncReport(0, 0, 0, 0, 0));

        new ApiCatalogSynchronizer(scanner, catalog).run(new DefaultApplicationArguments());

        verify(catalog).synchronize(found);
    }
}

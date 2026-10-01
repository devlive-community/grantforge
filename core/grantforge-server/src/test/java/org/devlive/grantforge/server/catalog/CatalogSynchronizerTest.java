// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.catalog;

import org.devlive.grantforge.authz.application.ApiCatalogService;
import org.devlive.grantforge.authz.application.ConsoleManifest;
import org.devlive.grantforge.authz.application.DeclaredEndpoint;
import org.devlive.grantforge.authz.application.ManifestReport;
import org.devlive.grantforge.authz.application.ManifestService;
import org.devlive.grantforge.authz.application.SyncReport;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.boot.DefaultApplicationArguments;

import java.util.List;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CatalogSynchronizerTest
{
    @Test
    void synchronizesTheApisAndThenTheManifest()
    {
        ApiEndpointScanner scanner = mock(ApiEndpointScanner.class);
        ApiCatalogService catalog = mock(ApiCatalogService.class);
        List<DeclaredEndpoint> found = List.of();
        when(scanner.scan()).thenReturn(found);
        when(catalog.synchronize(found)).thenReturn(new SyncReport(0, 0, 0, 0, 0));
        ConsoleManifestLoader manifests = mock(ConsoleManifestLoader.class);
        ManifestService manifest = mock(ManifestService.class);
        ConsoleManifest declared = new ConsoleManifest(List.of());
        when(manifests.load()).thenReturn(declared);
        when(manifest.synchronize(declared)).thenReturn(new ManifestReport(0, 0, 0, 0, 0));

        new CatalogSynchronizer(scanner, catalog, manifests, manifest).run(new DefaultApplicationArguments());

        InOrder order = inOrder(catalog, manifest);
        order.verify(catalog).synchronize(found);
        order.verify(manifest).synchronize(declared);
    }
}

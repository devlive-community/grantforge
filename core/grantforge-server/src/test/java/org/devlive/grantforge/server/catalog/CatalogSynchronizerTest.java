// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.catalog;

import org.devlive.grantforge.authz.application.ApiCatalogService;
import org.devlive.grantforge.authz.application.ConsoleManifest;
import org.devlive.grantforge.authz.application.DataEntityCatalog;
import org.devlive.grantforge.authz.application.DeclaredEndpoint;
import org.devlive.grantforge.authz.application.FieldAppearance;
import org.devlive.grantforge.authz.application.ManifestReport;
import org.devlive.grantforge.authz.application.ManifestService;
import org.devlive.grantforge.authz.application.SyncReport;
import org.devlive.grantforge.persistence.secured.SecuredEntities;
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
    void synchronizesTheApisEntitiesAndFieldsAndThenTheManifest()
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

        DataEntityCatalog entities = mock(DataEntityCatalog.class);
        SecuredEntities secured = SecuredEntities.none();
        SecuredFieldScanner fields = mock(SecuredFieldScanner.class);
        List<FieldAppearance> appearances = List.of();
        when(fields.scan()).thenReturn(appearances);

        new CatalogSynchronizer(scanner, catalog, manifests, manifest, entities, secured, fields).run(new DefaultApplicationArguments());

        InOrder order = inOrder(catalog, entities, manifest);
        order.verify(catalog).synchronize(found);
        order.verify(entities).synchronize(secured.all());
        order.verify(entities).synchronizeFields(appearances);
        order.verify(manifest).synchronize(declared);
    }
}

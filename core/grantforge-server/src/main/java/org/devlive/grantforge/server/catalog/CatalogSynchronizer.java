// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.catalog;

import org.devlive.grantforge.authz.application.ApiCatalogService;
import org.devlive.grantforge.authz.application.DataEntityCatalog;
import org.devlive.grantforge.authz.application.FieldAppearance;
import org.devlive.grantforge.authz.application.ManifestReport;
import org.devlive.grantforge.authz.application.ManifestService;
import org.devlive.grantforge.authz.application.SyncReport;
import org.devlive.grantforge.persistence.secured.SecuredEntities;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.stereotype.Component;

import java.util.List;

import static java.util.Objects.requireNonNull;

/**
 * Brings the resource catalog in step with the code at start-up: first the API catalog from the controllers, the
 * secured entities and the secured fields the APIs return or accept, then the console's pages and buttons from its permission manifest, which refers to the API permissions. A controller
 * method without an access annotation, or a manifest that does not fit, stops the start, so nothing goes
 * unprotected or undeclared by accident.
 */
@Component
@ConditionalOnWebApplication
public final class CatalogSynchronizer
        implements ApplicationRunner
{
    private static final Logger LOG = LoggerFactory.getLogger(CatalogSynchronizer.class);

    private final ApiEndpointScanner scanner;
    private final ApiCatalogService catalog;
    private final ConsoleManifestLoader manifests;
    private final ManifestService manifest;
    private final DataEntityCatalog entities;
    private final SecuredEntities secured;
    private final SecuredFieldScanner fields;

    /**
     * Creates the synchronizer.
     *
     * @param scanner lists the served endpoints
     * @param catalog the API catalog
     * @param manifests reads the console's permission manifest
     * @param manifest synchronizes the manifest
     * @param entities registers the secured entities
     * @param secured the secured entities the code declares
     * @param fields lists where the APIs return or accept secured fields
     */
    public CatalogSynchronizer(ApiEndpointScanner scanner, ApiCatalogService catalog, ConsoleManifestLoader manifests,
            ManifestService manifest, DataEntityCatalog entities, SecuredEntities secured, SecuredFieldScanner fields)
    {
        this.fields = requireNonNull(fields, "fields");
        this.scanner = requireNonNull(scanner, "scanner");
        this.catalog = requireNonNull(catalog, "catalog");
        this.manifests = requireNonNull(manifests, "manifests");
        this.manifest = requireNonNull(manifest, "manifest");
        this.entities = requireNonNull(entities, "entities");
        this.secured = requireNonNull(secured, "secured");
    }

    @Override
    public void run(ApplicationArguments args)
    {
        SyncReport apis = catalog.synchronize(scanner.scan());
        LOG.info("API catalog: {} endpoints, {} added, {} changed, {} removed, {} new permissions", apis.endpoints(),
                apis.added(), apis.changed(), apis.removed(), apis.permissions());
        int registered = entities.synchronize(secured.all());
        LOG.info("Data entities: {} secured, {} added", secured.all().size(), registered);
        List<FieldAppearance> appearances = fields.scan();
        int declared = entities.synchronizeFields(appearances);
        LOG.info("Secured fields: {} appearances in APIs, {} fields added", appearances.size(), declared);
        ManifestReport console = manifest.synchronize(manifests.load());
        LOG.info("Console manifest: {} resources, {} created, {} updated, {} dependencies added, {} removed",
                console.resources(), console.created(), console.updated(), console.dependenciesAdded(),
                console.dependenciesRemoved());
    }
}

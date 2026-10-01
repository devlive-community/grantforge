// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.catalog;

import org.devlive.grantforge.authz.application.ApiCatalogService;
import org.devlive.grantforge.authz.application.SyncReport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.stereotype.Component;

import static java.util.Objects.requireNonNull;

/**
 * Brings the API catalog in step with the controllers at start-up. A controller method without an access
 * annotation stops the start, so no endpoint goes unprotected by accident.
 */
@Component
@ConditionalOnWebApplication
public final class ApiCatalogSynchronizer
        implements ApplicationRunner
{
    private static final Logger LOG = LoggerFactory.getLogger(ApiCatalogSynchronizer.class);

    private final ApiEndpointScanner scanner;
    private final ApiCatalogService catalog;

    /**
     * Creates the synchronizer.
     *
     * @param scanner lists the served endpoints
     * @param catalog the API catalog
     */
    public ApiCatalogSynchronizer(ApiEndpointScanner scanner, ApiCatalogService catalog)
    {
        this.scanner = requireNonNull(scanner, "scanner");
        this.catalog = requireNonNull(catalog, "catalog");
    }

    @Override
    public void run(ApplicationArguments args)
    {
        SyncReport report = catalog.synchronize(scanner.scan());
        LOG.info("API catalog: {} endpoints, {} added, {} changed, {} removed, {} new permissions", report.endpoints(),
                report.added(), report.changed(), report.removed(), report.permissions());
    }
}

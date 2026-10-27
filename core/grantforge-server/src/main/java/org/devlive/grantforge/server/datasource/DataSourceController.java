// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.datasource;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.devlive.grantforge.common.security.RequirePermission;
import org.devlive.grantforge.common.security.RequireStepUp;
import org.devlive.grantforge.datasource.application.DataSourceCommand;
import org.devlive.grantforge.datasource.application.DataSourceService;
import org.devlive.grantforge.datasource.application.DataSourceView;
import org.devlive.grantforge.datasource.application.DirectoryReport;
import org.devlive.grantforge.server.security.SessionUser;
import org.devlive.grantforge.server.web.PathIds;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Organization data source administration, separate from authentication sources.
 */
@RestController
@RequestMapping("/api/v1/data-sources")
public final class DataSourceController
{
    private final DataSourceService sources;

    /**
     * Creates the API.
     *
     * @param sources organization data sources
     */
    public DataSourceController(DataSourceService sources)
    {
        this.sources = sources;
    }

    /**
     * Lists configurations without credentials.
     *
     * @return tenant data sources
     */
    @GetMapping
    @RequirePermission("system.data-source.read")
    public List<DataSourceView> list()
    {
        return sources.list();
    }

    /**
     * Creates an organization data source.
     *
     * @param user    administrator
     * @param request settings and write-only credential
     * @return public configuration
     */
    @PostMapping
    @RequirePermission("system.data-source.create")
    @RequireStepUp
    @ResponseStatus(HttpStatus.CREATED)
    public DataSourceView create(@AuthenticationPrincipal SessionUser user, @Valid @RequestBody DataSourceRequest request)
    {
        return sources.create(user.accountId(), request.command());
    }

    /**
     * Updates an organization data source.
     *
     * @param user    administrator
     * @param id      source ID
     * @param request settings
     * @return public configuration
     */
    @PutMapping("/{id}")
    @RequirePermission("system.data-source.update")
    @RequireStepUp
    public DataSourceView update(@AuthenticationPrincipal SessionUser user, @PathVariable String id, @Valid @RequestBody DataSourceRequest request)
    {
        return sources.update(user.accountId(), PathIds.parse(id, "data source"), request.command());
    }

    /**
     * Tests complete directory read permissions without writing organization data.
     *
     * @param id source ID
     */
    @PostMapping("/{id}/test")
    @RequirePermission("system.data-source.update")
    @RequireStepUp
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void test(@PathVariable String id)
    {
        sources.test(PathIds.parse(id, "data source"));
    }

    /**
     * Runs an atomic organization sync.
     *
     * @param user administrator
     * @param id   source ID
     * @return applied counts
     */
    @PostMapping("/{id}/sync")
    @RequirePermission("system.data-source.sync")
    @RequireStepUp
    public DirectoryReport sync(@AuthenticationPrincipal SessionUser user, @PathVariable String id)
    {
        return sources.sync(user.accountId(), PathIds.parse(id, "data source"));
    }

    /**
     * Removes an unused source without deleting organization grants.
     *
     * @param user administrator
     * @param id   source ID
     */
    @DeleteMapping("/{id}")
    @RequirePermission("system.data-source.delete")
    @RequireStepUp
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal SessionUser user, @PathVariable String id)
    {
        sources.delete(user.accountId(), PathIds.parse(id, "data source"));
    }

    /**
     * Public configuration with a separate write-only credential; IDs remain strings in the browser.
     *
     * @param code                tenant code
     * @param name                display name
     * @param type                provider type
     * @param identitySourceId    OIDC authority
     * @param enabled             whether synchronization is enabled
     * @param loginUpdates        whether logins reconcile departments
     * @param settings            public provider configuration
     * @param parentUnitId        optional local mount
     * @param syncIntervalMinutes optional schedule
     * @param secret              credential; omitted or blank preserves it
     */
    public record DataSourceRequest(@NotBlank @Size(max = 64) String code, @NotBlank @Size(max = 128) String name,
                                    @NotBlank @Size(max = 32) String type, @NotBlank String identitySourceId,
                                    @Nullable Boolean enabled, @Nullable Boolean loginUpdates,
                                    @NotNull Map<String, String> settings, @Nullable String parentUnitId,
                                    @Nullable Integer syncIntervalMinutes,
                                    @Size(max = 512) @Nullable String secret) {
        /**
         * Converts public IDs and configuration.
         *
         * @return validated application input
         */
        public DataSourceCommand command()
        {
            return new DataSourceCommand(code, name, type, PathIds.parse(identitySourceId, "identity source"), enabled == null || enabled, loginUpdates == null || loginUpdates, settings,
                parentUnitId == null || parentUnitId.isBlank() ? null : PathIds.parse(parentUnitId, "mount department"), syncIntervalMinutes, secret);
        }

        /**
         * Hides credentials from logs.
         */
        @Override
        public String toString()
        {
            return "DataSourceRequest[" + code + ", " + type + "]";
        }
    }
}

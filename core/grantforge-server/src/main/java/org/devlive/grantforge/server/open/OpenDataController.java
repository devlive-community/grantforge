// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.open;

import jakarta.validation.Valid;
import org.devlive.grantforge.authz.data.ApplicationEntityService;
import org.devlive.grantforge.authz.data.DataScopes;
import org.devlive.grantforge.authz.domain.Application;
import org.devlive.grantforge.authz.domain.ApplicationRepository;
import org.devlive.grantforge.common.error.CommonErrorCode;
import org.devlive.grantforge.common.error.GrantForgeException;
import org.devlive.grantforge.common.security.AuthenticatedEndpoint;
import org.devlive.grantforge.oauth.application.OAuthSubject;
import org.devlive.grantforge.oauth.application.OpenCaller;
import org.devlive.grantforge.server.security.SecurityErrorCode;
import org.jspecify.annotations.Nullable;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.WebRequest;

import static java.util.Objects.requireNonNull;

/**
 * Data permissions of applications (D-67): an application declares its data entities with a token of its own and the
 * {@value #CATALOG} scope; for a user, it reads what the user's roles say about those entities and applies the rules to
 * its own rows.
 */
@RestController
public final class OpenDataController
{
    /** Scope a client's own token needs to declare entities. */
    static final String CATALOG = "catalog";

    private final ApplicationEntityService entities;
    private final DataScopes scopes;
    private final ApplicationRepository applications;

    /**
     * Creates the controller.
     *
     * @param entities takes in declared entities
     * @param scopes works out data access
     * @param applications the catalog's applications, for their codes
     */
    public OpenDataController(ApplicationEntityService entities, DataScopes scopes, ApplicationRepository applications)
    {
        this.entities = requireNonNull(entities, "entities");
        this.scopes = requireNonNull(scopes, "scopes");
        this.applications = requireNonNull(applications, "applications");
    }

    /**
     * Replaces the application's data entities with those it declares now, as it does at start-up.
     *
     * @param caller the client, with a token of its own
     * @param body the entities
     * @return how many the application has now
     */
    @AuthenticatedEndpoint
    @PutMapping("/api/v1/open/catalog/data-entities")
    public OpenEntitiesResponse declareOpenDataEntities(@AuthenticationPrincipal OpenCaller caller, @Valid @RequestBody OpenEntitiesRequest body)
    {
        if (caller.subject() != null) {
            throw new GrantForgeException(SecurityErrorCode.OPEN_CLIENT_REQUIRED, "client " + caller.clientId() + " declared for a user");
        }
        if (!caller.scopes().contains(CATALOG)) {
            throw new GrantForgeException(SecurityErrorCode.OPEN_SCOPE_REQUIRED, "token of client " + caller.clientId()
                    + " lacks the catalog scope");
        }
        return new OpenEntitiesResponse(entities.declare(caller.applicationId(), body.declarations()));
    }

    /**
     * Returns what the user's roles say about the application's data entities; {@code If-None-Match} answers 304 while
     * nothing changed.
     *
     * @param caller the application and user of the token
     * @param request the request, for its {@code If-None-Match}
     * @return the rules, or 304
     */
    @AuthenticatedEndpoint
    @GetMapping("/api/v1/open/me/data-access")
    public @Nullable ResponseEntity<OpenDataAccessResponse> openDataAccess(@AuthenticationPrincipal OpenCaller caller, WebRequest request)
    {
        OAuthSubject subject = caller.subject();
        if (subject == null) {
            throw new GrantForgeException(SecurityErrorCode.OPEN_USER_REQUIRED, "client " + caller.clientId() + " called for no user");
        }
        if (!caller.mayReadPermissions()) {
            throw new GrantForgeException(SecurityErrorCode.OPEN_SCOPE_REQUIRED, "token of client " + caller.clientId()
                    + " lacks the permissions scope");
        }
        String application = applications.findById(caller.applicationId()).map(Application::getCode)
                .orElseThrow(() -> new GrantForgeException(CommonErrorCode.NOT_FOUND, "no application " + caller.applicationId()));
        OpenDataAccessResponse access = OpenDataAccessResponse.from(scopes.accessIn(subject.accountId(), application));
        String etag = "\"" + access.version() + "\"";
        if (request.checkNotModified(etag)) {
            return null;
        }
        return ResponseEntity.ok().eTag(etag).cacheControl(CacheControl.noCache().cachePrivate()).body(access);
    }
}

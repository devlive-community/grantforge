// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.open;

import org.devlive.grantforge.authz.application.AuthorizationEvaluator;
import org.devlive.grantforge.authz.application.AuthorizationSnapshot;
import org.devlive.grantforge.authz.domain.Application;
import org.devlive.grantforge.authz.domain.ApplicationRepository;
import org.devlive.grantforge.common.error.CommonErrorCode;
import org.devlive.grantforge.common.error.GrantForgeException;
import org.devlive.grantforge.common.security.AuthenticatedEndpoint;
import org.devlive.grantforge.oauth.application.OAuthSubject;
import org.devlive.grantforge.oauth.application.OpenCaller;
import org.devlive.grantforge.server.security.AuthorizationResponse;
import org.devlive.grantforge.server.security.SecurityErrorCode;
import org.jspecify.annotations.Nullable;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.WebRequest;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static java.util.Objects.requireNonNull;

/**
 * What the user a token was issued for may do in the token's application (D-66). The token needs the
 * {@value OpenCaller#PERMISSIONS} scope; answers cover the application's own catalog only, never another's.
 */
@RestController
@RequestMapping("/api/v1/open/me")
public final class OpenAuthorizationController
{
    /** Most permissions one check asks about. */
    static final int MAX_CHECKED = 100;

    private final AuthorizationEvaluator evaluator;
    private final ApplicationRepository applications;

    /**
     * Creates the controller.
     *
     * @param evaluator works out permissions
     * @param applications the catalog's applications, for their codes
     */
    public OpenAuthorizationController(AuthorizationEvaluator evaluator, ApplicationRepository applications)
    {
        this.evaluator = requireNonNull(evaluator, "evaluator");
        this.applications = requireNonNull(applications, "applications");
    }

    /**
     * Returns everything the user may do in the application. Applications keep it and ask again with
     * {@code If-None-Match}, which answers 304 while nothing changed.
     *
     * @param caller the application and user of the token
     * @param request the request, for its {@code If-None-Match}
     * @return the permissions, or 304
     */
    @AuthenticatedEndpoint
    @GetMapping("/authorization")
    public @Nullable ResponseEntity<OpenAuthorizationResponse> openAuthorization(@AuthenticationPrincipal OpenCaller caller, WebRequest request)
    {
        OAuthSubject subject = userOf(caller);
        AuthorizationSnapshot snapshot = evaluator.snapshot(subject.accountId(), caller.applicationId());
        long version = AuthorizationResponse.versionOf(snapshot, Map.of());
        String etag = "\"" + version + "\"";
        if (request.checkNotModified(etag)) {
            return null;
        }
        String application = applications.findById(caller.applicationId()).map(Application::getCode).orElse("");
        return ResponseEntity.ok().eTag(etag).cacheControl(CacheControl.noCache().cachePrivate())
                .body(new OpenAuthorizationResponse(application, Long.toString(subject.accountId()), Long.toString(subject.tenantId()),
                        subject.username(), version, sorted(snapshot.roles()), sorted(snapshot.resources()),
                        sorted(snapshot.permissions()), snapshot.computedAt()));
    }

    /**
     * Answers whether the user holds API permissions of the application.
     *
     * @param caller the application and user of the token
     * @param permission the permission codes, 1 to {@value #MAX_CHECKED}
     * @return each permission and whether the user holds it
     * @throws GrantForgeException with {@code BAD_REQUEST} for no or too many permissions
     */
    @AuthenticatedEndpoint
    @GetMapping("/permissions")
    public OpenCheckResponse checkOpenPermissions(@AuthenticationPrincipal OpenCaller caller,
            @RequestParam List<String> permission)
    {
        if (permission.isEmpty() || permission.size() > MAX_CHECKED) {
            throw new GrantForgeException(CommonErrorCode.BAD_REQUEST, "ask about 1 to " + MAX_CHECKED + " permissions");
        }
        OAuthSubject subject = userOf(caller);
        AuthorizationSnapshot snapshot = evaluator.snapshot(subject.accountId(), caller.applicationId());
        Map<String, Boolean> answers = new LinkedHashMap<>();
        permission.forEach(code -> answers.put(code, snapshot.holds(code)));
        return new OpenCheckResponse(answers);
    }

    private static OAuthSubject userOf(OpenCaller caller)
    {
        OAuthSubject subject = caller.subject();
        if (subject == null) {
            throw new GrantForgeException(SecurityErrorCode.OPEN_USER_REQUIRED, "client " + caller.clientId() + " called for no user");
        }
        if (!caller.mayReadPermissions()) {
            throw new GrantForgeException(SecurityErrorCode.OPEN_SCOPE_REQUIRED, "token of client " + caller.clientId()
                    + " lacks the permissions scope");
        }
        return subject;
    }

    private static List<String> sorted(Collection<String> values)
    {
        return values.stream().sorted().toList();
    }
}

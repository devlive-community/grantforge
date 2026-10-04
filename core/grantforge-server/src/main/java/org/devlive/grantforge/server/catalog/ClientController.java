// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.catalog;

import jakarta.validation.Valid;
import org.devlive.grantforge.authz.application.OAuthClientService;
import org.devlive.grantforge.common.security.RequirePermission;
import org.devlive.grantforge.common.security.RequireStepUp;
import org.devlive.grantforge.server.security.SessionUser;
import org.devlive.grantforge.server.web.PathIds;
import org.jspecify.annotations.Nullable;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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

import java.time.Duration;
import java.util.List;

import static java.util.Objects.requireNonNull;
import static java.util.Objects.requireNonNullElse;

/**
 * The OAuth clients of catalog applications, through which they sign users in and obtain tokens. Platform administrators
 * manage them; secrets are returned once, never cached.
 */
@RestController
@RequestMapping("/api/v1")
public final class ClientController
{
    private final OAuthClientService clients;

    /**
     * Creates the controller.
     *
     * @param clients the clients
     */
    public ClientController(OAuthClientService clients)
    {
        this.clients = requireNonNull(clients, "clients");
    }

    /**
     * Lists an application's clients, oldest first.
     *
     * @param id the application
     * @return the clients
     */
    @RequirePermission("platform.client.read")
    @GetMapping("/applications/{id}/clients")
    public List<ClientResponse> applicationClients(@PathVariable String id)
    {
        return clients.list(PathIds.parse(id, "application")).stream().map(ClientResponse::from).toList();
    }

    /**
     * Registers a client of an application.
     *
     * @param user the session's principal
     * @param id the application
     * @param body the client
     * @return the client, with its secret if confidential
     */
    @RequireStepUp
    @RequirePermission("platform.client.create")
    @PostMapping("/applications/{id}/clients")
    public ResponseEntity<IssuedClientResponse> createClient(@AuthenticationPrincipal SessionUser user, @PathVariable String id,
            @Valid @RequestBody ClientRequest body)
    {
        return secret(HttpStatus.CREATED, IssuedClientResponse.from(clients.register(user.accountId(), PathIds.parse(id, "application"),
                requireNonNull(body.type(), "type"), requireNonNull(body.settings(), "settings").settings())));
    }

    /**
     * Changes what a client may do.
     *
     * @param user the session's principal
     * @param id the client
     * @param body what it may do
     * @return the client
     */
    @RequirePermission("platform.client.update")
    @PutMapping("/clients/{id}")
    public ClientResponse updateClient(@AuthenticationPrincipal SessionUser user, @PathVariable String id,
            @Valid @RequestBody ClientSettingsRequest body)
    {
        return ClientResponse.from(clients.update(user.accountId(), PathIds.parse(id, "client"), body.settings()));
    }

    /**
     * Gives a confidential client a new secret.
     *
     * @param user the session's principal
     * @param id the client
     * @param body how long the current secret keeps working
     * @return the client with its new secret
     */
    @RequireStepUp
    @RequirePermission("platform.client.rotate")
    @PostMapping("/clients/{id}/rotate-secret")
    public ResponseEntity<IssuedClientResponse> rotateClientSecret(@AuthenticationPrincipal SessionUser user, @PathVariable String id,
            @Valid @RequestBody(required = false) @Nullable ClientRotateRequest body)
    {
        int hours = body == null ? 0 : requireNonNullElse(body.graceHours(), 0);
        return secret(HttpStatus.OK, IssuedClientResponse.from(clients.rotateSecret(user.accountId(), PathIds.parse(id, "client"),
                Duration.ofHours(hours))));
    }

    /**
     * Deletes a client.
     *
     * @param user the session's principal
     * @param id the client
     */
    @RequirePermission("platform.client.delete")
    @DeleteMapping("/clients/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteClient(@AuthenticationPrincipal SessionUser user, @PathVariable String id)
    {
        clients.delete(user.accountId(), PathIds.parse(id, "client"));
    }

    private static ResponseEntity<IssuedClientResponse> secret(HttpStatus status, IssuedClientResponse body)
    {
        return ResponseEntity.status(status).cacheControl(CacheControl.noStore()).body(body);
    }
}

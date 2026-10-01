// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.identity.api;

import jakarta.validation.Valid;
import org.devlive.grantforge.identity.application.SecurityProperties;
import org.devlive.grantforge.identity.application.SetupCommand;
import org.devlive.grantforge.identity.application.SetupResult;
import org.devlive.grantforge.identity.application.SetupService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static java.util.Objects.requireNonNull;

/** Public endpoints used before sign-in: console bootstrap state and first-run setup. */
@RestController
@RequestMapping("/api/v1")
public final class SetupController
{
    private final SetupService setup;
    private final SecurityProperties security;

    /**
     * Creates the controller.
     *
     * @param setup the setup service
     * @param security security settings
     */
    public SetupController(SetupService setup, SecurityProperties security)
    {
        this.setup = requireNonNull(setup, "setup");
        this.security = requireNonNull(security, "security");
    }

    /**
     * Returns whether setup is pending and whether self-registration is enabled.
     *
     * @return the bootstrap state
     */
    @GetMapping("/bootstrap")
    public BootstrapResponse bootstrap()
    {
        return new BootstrapResponse(setup.isRequired(), security.registrationEnabled());
    }

    /**
     * Completes first-run setup.
     *
     * @param request the validated setup input
     * @return the created tenant code and administrator login name
     */
    @PostMapping("/setup")
    public SetupResponse completeSetup(@Valid @RequestBody SetupRequest request)
    {
        // Bean validation guarantees these are present.
        SetupResult result = setup.complete(new SetupCommand(request.token(), request.tenantName(),
                requireNonNull(request.username()).strip(), requireNonNull(request.password()),
                request.displayName()));
        return new SetupResponse(result.tenantCode(), result.username());
    }
}

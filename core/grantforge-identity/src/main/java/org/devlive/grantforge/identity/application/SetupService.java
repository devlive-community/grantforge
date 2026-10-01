// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.identity.application;

import org.devlive.grantforge.common.error.CommonErrorCode;
import org.devlive.grantforge.common.error.GrantForgeException;
import org.devlive.grantforge.common.lang.Strings;
import org.devlive.grantforge.identity.domain.PlatformSetting;
import org.devlive.grantforge.identity.domain.PlatformSettingRepository;
import org.devlive.grantforge.identity.domain.Tenant;
import org.devlive.grantforge.identity.domain.TenantRepository;
import org.devlive.grantforge.identity.domain.UserAccount;
import org.devlive.grantforge.identity.domain.UserAccountRepository;
import org.devlive.grantforge.persistence.tenant.TenantContext;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;

import static java.util.Objects.requireNonNull;

/**
 * First-run setup (D-21): while the platform has no administrator, a one-time token authorizes creating the
 * first tenant and its administrator. Only the token's SHA-256 hash is stored; the token itself is logged
 * once at start-up (or configured through {@code grantforge.setup.token}). Completing setup records a
 * marker with a unique key, so setup can succeed only once even when requests race.
 */
@Service
public final class SetupService
{
    /** Setting holding the hex SHA-256 hash of the current setup token. */
    static final String TOKEN_HASH = "setup.token-sha256";
    /** Setting whose presence means setup is complete; its value is the completion time. */
    static final String COMPLETED_AT = "setup.completed-at";
    /** Code of the tenant created by setup. */
    static final String DEFAULT_TENANT_CODE = "default";
    /** Tenant name used when the request leaves it empty. */
    static final String DEFAULT_TENANT_NAME = "Default";

    private static final Logger LOG = LoggerFactory.getLogger(SetupService.class);
    private static final int TOKEN_BYTES = 32;

    private final PlatformSettingRepository settings;
    private final TenantRepository tenants;
    private final UserAccountRepository accounts;
    private final PasswordEncoder passwordEncoder;
    private final PasswordPolicy passwordPolicy;
    private final SetupProperties properties;
    private final TransactionTemplate transactions;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    /**
     * Creates the service.
     *
     * @param settings platform settings
     * @param tenants tenants
     * @param accounts user accounts
     * @param passwordEncoder hashes the administrator password
     * @param passwordPolicy validates the administrator password
     * @param properties setup settings
     * @param transactionManager runs the setup writes atomically
     * @param clock source of the current time
     */
    public SetupService(PlatformSettingRepository settings, TenantRepository tenants, UserAccountRepository accounts,
            PasswordEncoder passwordEncoder, PasswordPolicy passwordPolicy, SetupProperties properties,
            PlatformTransactionManager transactionManager, Clock clock)
    {
        this.settings = requireNonNull(settings, "settings");
        this.tenants = requireNonNull(tenants, "tenants");
        this.accounts = requireNonNull(accounts, "accounts");
        this.passwordEncoder = requireNonNull(passwordEncoder, "passwordEncoder");
        this.passwordPolicy = requireNonNull(passwordPolicy, "passwordPolicy");
        this.properties = requireNonNull(properties, "properties");
        this.transactions = new TransactionTemplate(requireNonNull(transactionManager, "transactionManager"));
        this.clock = requireNonNull(clock, "clock");
    }

    /**
     * Returns whether first-run setup still has to happen.
     *
     * @return {@code true} until setup completed
     */
    public boolean isRequired()
    {
        return settings.findBySettingKey(COMPLETED_AT).isEmpty();
    }

    /**
     * Stores the hash of a new setup token, replacing any previous one. Does nothing once setup completed.
     *
     * @return the generated token, which the caller must show to the operator; empty when setup is complete
     *         or the token is configured (the operator already knows it)
     */
    public Optional<String> issueToken()
    {
        if (!isRequired()) {
            return Optional.empty();
        }
        String configured = properties.token();
        String token = configured != null ? configured : generateToken();
        String hash = sha256(token);
        transactions.executeWithoutResult(status -> settings.findBySettingKey(TOKEN_HASH).ifPresentOrElse(
                existing -> existing.update(hash),
                () -> settings.save(PlatformSetting.of(TOKEN_HASH, hash))));
        return configured != null ? Optional.empty() : Optional.of(token);
    }

    /**
     * Creates the first tenant and administrator and closes setup for good.
     *
     * @param command the setup input
     * @return the created tenant code and login name
     * @throws GrantForgeException {@link IdentityErrorCode#SETUP_COMPLETED} if setup already happened,
     *         {@link IdentityErrorCode#SETUP_TOKEN_INVALID} for a wrong token, a password policy error, or
     *         {@link CommonErrorCode#BAD_REQUEST} for an invalid name
     */
    public SetupResult complete(SetupCommand command)
    {
        requireNonNull(command, "command");
        if (!isRequired()) {
            throw new GrantForgeException(IdentityErrorCode.SETUP_COMPLETED, "setup already completed");
        }
        if (!tokenMatches(command.token())) {
            throw new GrantForgeException(IdentityErrorCode.SETUP_TOKEN_INVALID, "setup token does not match");
        }
        passwordPolicy.check(command.password(), command.username());

        Instant now = clock.instant();
        Tenant tenant;
        UserAccount administrator;
        try {
            String tenantName = Strings.blankToNull(command.tenantName());
            tenant = Tenant.create(DEFAULT_TENANT_CODE, tenantName == null ? DEFAULT_TENANT_NAME : tenantName);
            administrator = UserAccount.create(command.username(), passwordEncoder.encode(command.password()), now)
                    .withDisplayName(command.displayName())
                    .markSystemAccount();
        }
        catch (IllegalArgumentException invalid) {
            throw new GrantForgeException(CommonErrorCode.BAD_REQUEST, String.valueOf(invalid.getMessage()), invalid);
        }

        try {
            // The tenant ID is preassigned, so the session can be bound to the new tenant before it exists.
            TenantContext.runInTenant(tenant.requireId(), () -> transactions.executeWithoutResult(status -> {
                settings.saveAndFlush(PlatformSetting.of(COMPLETED_AT, now.toString()));
                tenants.save(tenant);
                accounts.save(administrator);
                settings.findBySettingKey(TOKEN_HASH).ifPresent(settings::delete);
            }));
        }
        catch (DataIntegrityViolationException race) {
            throw new GrantForgeException(IdentityErrorCode.SETUP_COMPLETED, "setup completed concurrently", race);
        }
        LOG.info("First-run setup completed: created tenant '{}' and administrator '{}'", tenant.getCode(),
                administrator.getUsername());
        return new SetupResult(tenant.getCode(), administrator.getUsername());
    }

    private boolean tokenMatches(@Nullable String token)
    {
        String candidate = Strings.blankToNull(token);
        String stored = settings.findBySettingKey(TOKEN_HASH).map(PlatformSetting::getSettingValue).orElse(null);
        if (candidate == null || stored == null) {
            return false;
        }
        // Constant-time comparison of equal-length hashes.
        return MessageDigest.isEqual(sha256(candidate).getBytes(StandardCharsets.US_ASCII),
                stored.getBytes(StandardCharsets.US_ASCII));
    }

    private String generateToken()
    {
        byte[] bytes = new byte[TOKEN_BYTES];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    static String sha256(String value)
    {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        }
        catch (NoSuchAlgorithmException impossible) {
            // Every Java platform must support SHA-256.
            throw new IllegalStateException("SHA-256 is not available", impossible);
        }
    }
}

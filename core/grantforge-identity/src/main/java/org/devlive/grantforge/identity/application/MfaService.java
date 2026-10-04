// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.identity.application;

import org.devlive.grantforge.audit.application.AuditLog;
import org.devlive.grantforge.audit.application.AuditRecord;
import org.devlive.grantforge.audit.domain.AuditAction;
import org.devlive.grantforge.audit.domain.AuditOutcome;
import org.devlive.grantforge.common.error.CommonErrorCode;
import org.devlive.grantforge.common.error.GrantForgeException;
import org.devlive.grantforge.identity.domain.MfaFactor;
import org.devlive.grantforge.identity.domain.MfaFactorRepository;
import org.devlive.grantforge.identity.domain.MfaRecoveryCode;
import org.devlive.grantforge.identity.domain.MfaRecoveryCodeRepository;
import org.devlive.grantforge.identity.domain.UserAccount;
import org.devlive.grantforge.identity.domain.UserAccountRepository;
import org.devlive.grantforge.persistence.tenant.TenantContext;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.OptionalLong;

import static java.util.Objects.requireNonNull;

/**
 * Two-step sign-in with an authenticator app (TOTP) and recovery codes (D-71). A user enrols an authenticator and confirms
 * it with a code, which turns two-step sign-in on and gives ten single-use recovery codes, shown once. A code works once:
 * an authenticator code not before a later one, a recovery code not again. Every method must be called with the account's
 * tenant bound.
 */
@Service
public final class MfaService
{
    /** How many recovery codes an account gets. */
    static final int RECOVERY_CODES = 10;

    private static final String ISSUER = "GrantForge";
    private static final String ALPHABET = "abcdefghjkmnpqrstuvwxyz23456789";
    private static final int SECRET_BYTES = 20;
    private static final int CODE_HALF = 5;

    private final MfaFactorRepository factors;
    private final MfaRecoveryCodeRepository recoveryCodes;
    private final UserAccountRepository accounts;
    private final SecretBox secrets;
    private final AuditLog audit;
    private final TransactionTemplate transactions;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    /**
     * Creates the service.
     *
     * @param factors the authenticators
     * @param recoveryCodes the recovery codes
     * @param accounts the accounts, for their names
     * @param secrets seals authenticator secrets
     * @param audit records turning it on and off
     * @param transactionManager opens transactions
     * @param clock the current time
     */
    public MfaService(MfaFactorRepository factors, MfaRecoveryCodeRepository recoveryCodes, UserAccountRepository accounts, SecretBox secrets,
            AuditLog audit, PlatformTransactionManager transactionManager, Clock clock)
    {
        this.factors = requireNonNull(factors, "factors");
        this.recoveryCodes = requireNonNull(recoveryCodes, "recoveryCodes");
        this.accounts = requireNonNull(accounts, "accounts");
        this.secrets = requireNonNull(secrets, "secrets");
        this.audit = requireNonNull(audit, "audit");
        this.transactions = new TransactionTemplate(requireNonNull(transactionManager, "transactionManager"));
        this.clock = requireNonNull(clock, "clock");
    }

    /**
     * Returns whether an account signs in in two steps.
     *
     * @param accountId the account
     * @return the status
     */
    public MfaStatus status(long accountId)
    {
        return requireNonNull(transactions.execute(status -> new MfaStatus(enabled(accountId),
                (int) recoveryCodes.findByAccountId(accountId).stream().filter(code -> !code.isUsed()).count())));
    }

    /**
     * Returns whether an account must give a second factor at sign-in.
     *
     * @param accountId the account
     * @return {@code true} once an authenticator is confirmed
     */
    public boolean enabled(long accountId)
    {
        return factors.findByAccountId(accountId).map(MfaFactor::isConfirmed).orElse(false);
    }

    /**
     * Starts setting up an authenticator, replacing one not confirmed yet.
     *
     * @param accountId the account
     * @return the secret and its otpauth link
     * @throws GrantForgeException with {@link IdentityErrorCode#MFA_ALREADY_ENABLED} while two-step sign-in is on
     */
    public MfaEnrollment enroll(long accountId)
    {
        byte[] key = new byte[SECRET_BYTES];
        random.nextBytes(key);
        String secret = Totp.base32(key);
        return requireNonNull(transactions.execute(status -> {
            UserAccount account = account(accountId);
            MfaFactor factor = factors.findByAccountId(accountId).orElse(null);
            if (factor != null && factor.isConfirmed()) {
                throw new GrantForgeException(IdentityErrorCode.MFA_ALREADY_ENABLED, "account " + accountId + " already has an authenticator");
            }
            if (factor == null) {
                factors.save(MfaFactor.enroll(accountId, secrets.seal(secret)));
            }
            else {
                factor.reenroll(secrets.seal(secret));
            }
            String label = encode(ISSUER + ":" + account.getUsername());
            return new MfaEnrollment(secret, "otpauth://totp/" + label + "?secret=" + secret + "&issuer=" + encode(ISSUER)
                    + "&algorithm=SHA1&digits=" + Totp.DIGITS + "&period=" + Totp.PERIOD);
        }));
    }

    /**
     * Confirms the authenticator being set up with a code from it, which turns two-step sign-in on.
     *
     * @param accountId the account
     * @param code a code the authenticator shows
     * @return the recovery codes, shown this once
     * @throws GrantForgeException with {@link IdentityErrorCode#MFA_NOT_ENROLLING} if none is being set up, or
     *         {@link IdentityErrorCode#MFA_CODE_INVALID}
     */
    public List<String> confirm(long accountId, String code)
    {
        return requireNonNull(transactions.execute(status -> {
            MfaFactor factor = factors.findByAccountId(accountId).filter(found -> !found.isConfirmed())
                    .orElseThrow(() -> new GrantForgeException(IdentityErrorCode.MFA_NOT_ENROLLING, "no authenticator to confirm"));
            Instant now = clock.instant();
            OptionalLong step = Totp.verify(key(factor), code, now);
            if (step.isEmpty()) {
                throw invalid();
            }
            factor.confirm(now, step.getAsLong());
            List<String> issued = issueRecoveryCodes(accountId);
            record(AuditAction.MFA_ENABLED, accountId, null);
            return issued;
        }));
    }

    /**
     * Checks a second factor: an authenticator code, or else an unused recovery code, which is used up.
     *
     * @param accountId the account
     * @param code the code entered
     * @return whether it is right; {@code false} also when two-step sign-in is off
     */
    public boolean verify(long accountId, String code)
    {
        return requireNonNull(transactions.execute(status -> {
            MfaFactor factor = factors.findByAccountId(accountId).filter(MfaFactor::isConfirmed).orElse(null);
            if (factor == null) {
                return false;
            }
            OptionalLong step = Totp.verify(key(factor), code, clock.instant());
            if (step.isPresent()) {
                return factor.use(step.getAsLong());
            }
            String hash = hash(normalize(code));
            MfaRecoveryCode recovery = recoveryCodes.findByAccountId(accountId).stream()
                    .filter(candidate -> !candidate.isUsed() && MessageDigest.isEqual(candidate.getCodeHash().getBytes(StandardCharsets.US_ASCII),
                            hash.getBytes(StandardCharsets.US_ASCII)))
                    .findFirst().orElse(null);
            if (recovery == null) {
                return false;
            }
            recovery.use(clock.instant());
            long left = recoveryCodes.findByAccountId(accountId).stream().filter(candidate -> !candidate.isUsed()).count();
            record(AuditAction.MFA_RECOVERY_CODE_USED, accountId, left + " left");
            return true;
        }));
    }

    /**
     * Turns two-step sign-in off, with a code that proves the user still has the authenticator or a recovery code.
     *
     * @param accountId the account
     * @param code a second factor
     * @throws GrantForgeException with {@link IdentityErrorCode#MFA_NOT_ENABLED} or {@link IdentityErrorCode#MFA_CODE_INVALID}
     */
    public void disable(long accountId, String code)
    {
        requireEnabled(accountId);
        if (!verify(accountId, code)) {
            throw invalid();
        }
        transactions.executeWithoutResult(status -> {
            remove(accountId);
            record(AuditAction.MFA_DISABLED, accountId, null);
        });
    }

    /**
     * Replaces the recovery codes with new ones, with a second factor.
     *
     * @param accountId the account
     * @param code a second factor
     * @return the new codes, shown this once
     * @throws GrantForgeException with {@link IdentityErrorCode#MFA_NOT_ENABLED} or {@link IdentityErrorCode#MFA_CODE_INVALID}
     */
    public List<String> renewRecoveryCodes(long accountId, String code)
    {
        requireEnabled(accountId);
        if (!verify(accountId, code)) {
            throw invalid();
        }
        return requireNonNull(transactions.execute(status -> {
            List<String> issued = issueRecoveryCodes(accountId);
            record(AuditAction.MFA_RECOVERY_CODES_RENEWED, accountId, null);
            return issued;
        }));
    }

    /**
     * Turns another account's two-step sign-in off, as an administrator does when its authenticator is lost; the user
     * then signs in with the password and sets an authenticator up again.
     *
     * @param actorId the administrator, who needs the permission to reset two-step sign-in, checked by the API
     * @param accountId the account
     * @throws GrantForgeException with {@link CommonErrorCode#NOT_FOUND} for an unknown account or
     *         {@link IdentityErrorCode#MFA_NOT_ENABLED}
     */
    public void reset(long actorId, long accountId)
    {
        transactions.executeWithoutResult(status -> {
            account(accountId);
            if (factors.findByAccountId(accountId).isEmpty()) {
                throw new GrantForgeException(IdentityErrorCode.MFA_NOT_ENABLED, "account " + accountId + " has no authenticator");
            }
            remove(accountId);
            audit.recordWithChange(new AuditRecord(AuditAction.MFA_RESET, AuditOutcome.SUCCESS, TenantContext.requireTenantId(), actorId, null,
                    Long.toString(accountId), null));
        });
    }

    private void requireEnabled(long accountId)
    {
        Boolean on = transactions.execute(status -> enabled(accountId));
        if (!Boolean.TRUE.equals(on)) {
            throw new GrantForgeException(IdentityErrorCode.MFA_NOT_ENABLED, "account " + accountId + " has two-step sign-in off");
        }
    }

    private void remove(long accountId)
    {
        factors.findByAccountId(accountId).ifPresent(factors::delete);
        recoveryCodes.deleteByAccount(accountId);
    }

    private List<String> issueRecoveryCodes(long accountId)
    {
        recoveryCodes.deleteByAccount(accountId);
        List<String> issued = new ArrayList<>();
        for (int i = 0; i < RECOVERY_CODES; i++) {
            String code = randomText(CODE_HALF) + "-" + randomText(CODE_HALF);
            issued.add(code);
            recoveryCodes.save(MfaRecoveryCode.of(accountId, hash(normalize(code))));
        }
        return List.copyOf(issued);
    }

    private void record(AuditAction action, long accountId, @Nullable String reason)
    {
        audit.recordWithChange(new AuditRecord(action, AuditOutcome.SUCCESS, TenantContext.requireTenantId(), accountId, null, null, reason));
    }

    private UserAccount account(long accountId)
    {
        return accounts.findById(accountId).orElseThrow(() -> new GrantForgeException(CommonErrorCode.NOT_FOUND, "no account " + accountId));
    }

    private byte[] key(MfaFactor factor)
    {
        return Totp.fromBase32(secrets.open(factor.getSecret()));
    }

    private String randomText(int length)
    {
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < length; i++) {
            text.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return text.toString();
    }

    private static String normalize(String code)
    {
        return code.replace("-", "").replace(" ", "").toLowerCase(Locale.ROOT);
    }

    private static String hash(String code)
    {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(code.getBytes(StandardCharsets.UTF_8)));
        }
        catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is not available", impossible);
        }
    }

    private static String encode(String text)
    {
        return URLEncoder.encode(text, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private static GrantForgeException invalid()
    {
        return new GrantForgeException(IdentityErrorCode.MFA_CODE_INVALID, "second factor rejected");
    }
}

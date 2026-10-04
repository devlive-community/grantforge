// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.oauth.application;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import org.devlive.grantforge.audit.application.AuditLog;
import org.devlive.grantforge.audit.application.AuditRecord;
import org.devlive.grantforge.audit.domain.AuditAction;
import org.devlive.grantforge.audit.domain.AuditOutcome;
import org.devlive.grantforge.authz.application.CatalogAccess;
import org.devlive.grantforge.identity.application.SecretBox;
import org.devlive.grantforge.oauth.domain.SigningKeyRecord;
import org.devlive.grantforge.oauth.domain.SigningKeyRepository;
import org.devlive.grantforge.persistence.tenant.TenantContext;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static java.util.Objects.requireNonNull;

/**
 * The keys the authorization server signs tokens with. One RSA key (RS256, which every OpenID Connect client supports)
 * signs; when it is rotated, by hand or after {@link OAuthProperties#signingKeyRotation()}, the next one takes over and the
 * old one stays in the published key set for {@link OAuthProperties#signingKeyRetention()}, so tokens it signed still
 * verify. Keys live in the database with the private half sealed by {@link SecretBox}, so every node of a cluster signs
 * with the same key and restarts keep it; each node rereads them every minute.
 */
@Component
public final class SigningKeys
{
    /** How long a node uses the keys it read before reading them again. */
    static final Duration REFRESH = Duration.ofMinutes(1);

    private static final Logger LOG = LoggerFactory.getLogger(SigningKeys.class);
    private static final int KEY_BITS = 2048;

    private final SigningKeyRepository keys;
    private final SecretBox secrets;
    private final CatalogAccess access;
    private final AuditLog audit;
    private final OAuthProperties properties;
    private final TransactionTemplate transactions;
    private final Clock clock;
    private final AtomicReference<@Nullable Loaded> loaded = new AtomicReference<>();

    /**
     * Creates the key store.
     *
     * @param keys the stored keys
     * @param secrets seals private keys
     * @param access who may rotate keys by hand: platform administrators
     * @param audit records rotations
     * @param properties when keys rotate and how long retired ones stay published
     * @param transactionManager opens transactions
     * @param clock the current time
     */
    public SigningKeys(SigningKeyRepository keys, SecretBox secrets, CatalogAccess access, AuditLog audit, OAuthProperties properties,
            PlatformTransactionManager transactionManager, Clock clock)
    {
        this.keys = requireNonNull(keys, "keys");
        this.secrets = requireNonNull(secrets, "secrets");
        this.access = requireNonNull(access, "access");
        this.audit = requireNonNull(audit, "audit");
        this.properties = requireNonNull(properties, "properties");
        this.transactions = new TransactionTemplate(requireNonNull(transactionManager, "transactionManager"));
        this.clock = requireNonNull(clock, "clock");
    }

    /**
     * Returns the published keys, public halves only: the active one and those retired recently.
     *
     * @return the source the key set endpoint and token decoders use
     */
    public JWKSource<SecurityContext> published()
    {
        return (selector, context) -> selector.select(new JWKSet(current().published()));
    }

    /**
     * Returns the active key, with its private half.
     *
     * @return the source the token encoder signs with
     */
    public JWKSource<SecurityContext> signing()
    {
        return (selector, context) -> selector.select(new JWKSet(current().active()));
    }

    /**
     * Lists the keys, newest first.
     *
     * @return the keys
     */
    public List<SigningKeyView> list()
    {
        current();
        return requireNonNull(transactions.execute(status -> keys.findAllByOrderByActivatedAtDescIdDesc().stream().map(this::view).toList()));
    }

    /**
     * Begins signing with a new key, as after a suspected leak; the current key stays published for the retention period.
     *
     * @param actorId the platform administrator asking
     * @return the new key
     */
    public SigningKeyView rotate(long actorId)
    {
        access.requireEditor(actorId);
        SigningKeyView key = requireNonNull(transactions.execute(status -> view(create(actorId, "requested"))));
        loaded.set(null);
        return key;
    }

    /**
     * Deletes keys retired longer than the retention period ago.
     *
     * @return how many went
     */
    int purgeRetired()
    {
        Instant before = clock.instant().minus(properties.signingKeyRetention());
        return requireNonNull(transactions.execute(status -> keys.deleteRetiredBefore(before)));
    }

    private Loaded current()
    {
        Instant now = clock.instant();
        Loaded known = loaded.get();
        if (known != null && known.readAt().plus(REFRESH).isAfter(now)) {
            return known;
        }
        Loaded fresh = requireNonNull(transactions.execute(status -> load(now)));
        loaded.set(fresh);
        return fresh;
    }

    private Loaded load(Instant now)
    {
        List<SigningKeyRecord> all = keys.findAllByOrderByActivatedAtDescIdDesc();
        List<SigningKeyRecord> active = all.stream().filter(key -> key.getRetiredAt() == null).toList();
        SigningKeyRecord signing;
        Duration rotation = properties.signingKeyRotation();
        if (active.isEmpty()) {
            signing = create(null, "first key");
            all = keys.findAllByOrderByActivatedAtDescIdDesc();
        }
        else if (!rotation.isZero() && active.get(0).getActivatedAt().plus(rotation).isBefore(now)) {
            signing = create(null, "rotation after " + rotation.toDays() + " days");
            all = keys.findAllByOrderByActivatedAtDescIdDesc();
        }
        else {
            signing = active.get(0);
            // Two nodes may have created a key at the same moment: the newest wins, the others retire.
            active.stream().skip(1).forEach(extra -> extra.retire(now));
        }
        Instant cutoff = now.minus(properties.signingKeyRetention());
        List<JWK> published = new ArrayList<>();
        for (SigningKeyRecord key : all) {
            Instant retiredAt = key.getRetiredAt();
            if (retiredAt == null || retiredAt.isAfter(cutoff)) {
                published.add(jwk(key, false));
            }
        }
        return new Loaded(jwk(signing, true), List.copyOf(published), now);
    }

    private SigningKeyRecord create(@Nullable Long actorId, String reason)
    {
        Instant now = clock.instant();
        KeyPair pair;
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(KEY_BITS);
            pair = generator.generateKeyPair();
        }
        catch (GeneralSecurityException impossible) {
            throw new IllegalStateException("RSA is not available", impossible);
        }
        RSAKey jwk = new RSAKey.Builder((RSAPublicKey) pair.getPublic()).build();
        String keyId;
        try {
            keyId = jwk.computeThumbprint().toString();
        }
        catch (JOSEException impossible) {
            throw new IllegalStateException("cannot compute a key thumbprint", impossible);
        }
        keys.findAllByOrderByActivatedAtDescIdDesc().stream().filter(key -> key.getRetiredAt() == null).forEach(key -> key.retire(now));
        SigningKeyRecord key = keys.saveAndFlush(SigningKeyRecord.create(keyId, JWSAlgorithm.RS256.getName(),
                Base64.getEncoder().encodeToString(pair.getPublic().getEncoded()),
                secrets.seal(Base64.getEncoder().encodeToString(pair.getPrivate().getEncoded())), now));
        LOG.info("The authorization server signs with a new key {} ({})", keyId, reason);
        audit.recordWithChange(new AuditRecord(AuditAction.SIGNING_KEY_ROTATED, AuditOutcome.SUCCESS, platformTenant(), actorId, null,
                keyId, reason));
        return key;
    }

    private static @Nullable Long platformTenant()
    {
        return TenantContext.currentTenantId().isPresent() ? TenantContext.currentTenantId().getAsLong() : null;
    }

    private JWK jwk(SigningKeyRecord key, boolean withPrivate)
    {
        try {
            KeyFactory factory = KeyFactory.getInstance("RSA");
            RSAPublicKey publicKey = (RSAPublicKey) factory.generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(key.getPublicKey())));
            RSAKey.Builder builder = new RSAKey.Builder(publicKey).keyID(key.getKeyId()).keyUse(KeyUse.SIGNATURE)
                    .algorithm(JWSAlgorithm.parse(key.getAlgorithm()));
            if (withPrivate) {
                byte[] encoded = Base64.getDecoder().decode(secrets.open(key.getPrivateKey()));
                builder.privateKey((RSAPrivateKey) factory.generatePrivate(new PKCS8EncodedKeySpec(encoded)));
            }
            return builder.build();
        }
        catch (GeneralSecurityException | IllegalArgumentException damaged) {
            throw new IllegalStateException("signing key " + key.getKeyId() + " cannot be read", damaged);
        }
    }

    private SigningKeyView view(SigningKeyRecord key)
    {
        Instant retiredAt = key.getRetiredAt();
        return new SigningKeyView(key.getKeyId(), key.getAlgorithm(), key.getActivatedAt(), retiredAt,
                retiredAt == null ? null : retiredAt.plus(properties.signingKeyRetention()));
    }

    /** Keys as one node read them. */
    private record Loaded(JWK active, List<JWK> published, Instant readAt)
    {
    }
}

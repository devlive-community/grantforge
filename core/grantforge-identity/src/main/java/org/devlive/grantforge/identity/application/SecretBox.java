// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.identity.application;

import org.devlive.grantforge.identity.domain.PlatformSetting;
import org.devlive.grantforge.identity.domain.PlatformSettingRepository;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static java.util.Objects.requireNonNull;

/**
 * Encrypts stored secrets (service passwords, signing keys, authenticator secrets) with AES-256-GCM. The key is
 * {@code grantforge.security.encryption-key} (32 bytes, Base64) when configured; otherwise one is generated once and
 * kept in the platform settings, so every node shares it. Keeping the key outside the database is safer: a copy of
 * the database alone then reveals no secret.
 */
@Component
public final class SecretBox
{
    /** Where a generated key is kept when none is configured. */
    static final String SETTING = "security.encryption-key";

    private static final Logger LOG = LoggerFactory.getLogger(SecretBox.class);
    private static final String VERSION = "v1:";
    private static final int KEY_BYTES = 32;
    private static final int NONCE_BYTES = 12;
    private static final int TAG_BITS = 128;

    private final @Nullable String configured;
    private final PlatformSettingRepository settings;
    private final TransactionTemplate own;
    private final SecureRandom random = new SecureRandom();
    private final AtomicReference<SecretKey> key = new AtomicReference<>();

    /**
     * Creates the box.
     *
     * @param configured the configured key, Base64; blank to keep a generated one in the platform settings
     * @param settings the platform settings
     * @param transactionManager opens a transaction of its own to store a generated key
     */
    // No configured key is null: the generated one is then used.
    @SuppressWarnings("PMD.NullAssignment")
    public SecretBox(@Value("${grantforge.security.encryption-key:}") @Nullable String configured, PlatformSettingRepository settings,
            PlatformTransactionManager transactionManager)
    {
        this.configured = configured == null || configured.isBlank() ? null : configured.strip();
        this.settings = requireNonNull(settings, "settings");
        this.own = new TransactionTemplate(requireNonNull(transactionManager, "transactionManager"));
        own.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /**
     * Encrypts a secret.
     *
     * @param plain the secret
     * @return the encrypted text, which says how it was encrypted
     * @throws IllegalStateException if the key is not 32 bytes or AES-GCM is not available
     */
    public String seal(String plain)
    {
        byte[] nonce = new byte[NONCE_BYTES];
        random.nextBytes(nonce);
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key(), new GCMParameterSpec(TAG_BITS, nonce));
            byte[] sealed = cipher.doFinal(requireNonNull(plain, "plain").getBytes(StandardCharsets.UTF_8));
            return VERSION + Base64.getEncoder().encodeToString(ByteBuffer.allocate(nonce.length + sealed.length).put(nonce)
                    .put(sealed).array());
        }
        catch (GeneralSecurityException impossible) {
            throw new IllegalStateException("AES-GCM is not available", impossible);
        }
    }

    /**
     * Decrypts a secret.
     *
     * @param sealed what {@link #seal(String)} returned
     * @return the secret
     * @throws IllegalStateException if the text was not sealed with this key or was changed
     */
    public String open(String sealed)
    {
        if (!requireNonNull(sealed, "sealed").startsWith(VERSION)) {
            throw new IllegalStateException("not a sealed secret");
        }
        try {
            byte[] bytes = Base64.getDecoder().decode(sealed.substring(VERSION.length()));
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(TAG_BITS, bytes, 0, NONCE_BYTES));
            return new String(cipher.doFinal(bytes, NONCE_BYTES, bytes.length - NONCE_BYTES), StandardCharsets.UTF_8);
        }
        catch (GeneralSecurityException | IllegalArgumentException wrong) {
            throw new IllegalStateException("the secret cannot be decrypted with this key", wrong);
        }
    }

    private SecretKey key()
    {
        SecretKey known = key.get();
        if (known == null) {
            known = new SecretKeySpec(decode(configured != null ? configured : stored()), "AES");
            key.compareAndSet(null, known);
        }
        return known;
    }

    private static byte[] decode(String text)
    {
        byte[] bytes = Base64.getDecoder().decode(text);
        if (bytes.length != KEY_BYTES) {
            throw new IllegalStateException("the encryption key must be 32 bytes, Base64-encoded");
        }
        return bytes;
    }

    /** The generated key from the platform settings, created by the first node that needs one. */
    private String stored()
    {
        Optional<String> existing = read();
        if (existing.isPresent()) {
            return existing.orElseThrow();
        }
        byte[] fresh = new byte[KEY_BYTES];
        random.nextBytes(fresh);
        String encoded = Base64.getEncoder().encodeToString(fresh);
        try {
            own.executeWithoutResult(status -> settings.saveAndFlush(PlatformSetting.of(SETTING, encoded)));
            LOG.warn("Generated a key for stored secrets and kept it in the database; set grantforge.security.encryption-key "
                    + "to keep secrets safe from a copy of the database alone");
            return encoded;
        }
        catch (DataIntegrityViolationException concurrent) {
            // Another node stored a key at the same moment: use that one.
            return read().orElseThrow(() -> new IllegalStateException("the stored encryption key vanished", concurrent));
        }
    }

    private Optional<String> read()
    {
        return requireNonNull(own.execute(status -> settings.findBySettingKey(SETTING)
                .flatMap(setting -> Optional.ofNullable(setting.getSettingValue()))));
    }
}

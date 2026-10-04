// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.oauth.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import org.devlive.grantforge.persistence.entity.BaseEntity;
import org.jspecify.annotations.Nullable;

import java.time.Instant;

import static java.util.Objects.requireNonNull;

/** A key the authorization server signs tokens with, its private half encrypted. */
@Entity
@Table(name = "gf_oauth_signing_key")
public class SigningKeyRecord
        extends BaseEntity
{
    @Column(name = "key_id", nullable = false, updatable = false, length = 64)
    private String keyId = "";

    @Column(name = "algorithm", nullable = false, updatable = false, length = 16)
    private String algorithm = "";

    @Column(name = "public_key", nullable = false, updatable = false, length = 1000)
    private String publicKey = "";

    @Lob
    @Column(name = "private_key", nullable = false, updatable = false)
    private String privateKey = "";

    @Column(name = "activated_at", nullable = false, updatable = false)
    private Instant activatedAt = Instant.EPOCH;

    @Column(name = "retired_at")
    private @Nullable Instant retiredAt;

    /** For JPA. */
    protected SigningKeyRecord()
    {
    }

    /**
     * Records a new active key.
     *
     * @param keyId the key ID tokens name in their header
     * @param algorithm the JWS algorithm, such as RS256
     * @param publicKey the public key, X.509 encoded in Base64
     * @param encryptedPrivateKey the private key, PKCS #8 encoded and encrypted
     * @param activatedAt when it began signing
     * @return the record
     */
    public static SigningKeyRecord create(String keyId, String algorithm, String publicKey, String encryptedPrivateKey, Instant activatedAt)
    {
        SigningKeyRecord key = new SigningKeyRecord();
        key.keyId = requireNonNull(keyId, "keyId");
        key.algorithm = requireNonNull(algorithm, "algorithm");
        key.publicKey = requireNonNull(publicKey, "publicKey");
        key.privateKey = requireNonNull(encryptedPrivateKey, "encryptedPrivateKey");
        key.activatedAt = requireNonNull(activatedAt, "activatedAt");
        return key;
    }

    /**
     * Stops the key signing; it stays published until what it signed has expired.
     *
     * @param now the current time
     */
    public void retire(Instant now)
    {
        if (retiredAt == null) {
            retiredAt = now;
        }
    }

    /**
     * Returns the key ID.
     *
     * @return the key ID
     */
    public String getKeyId()
    {
        return keyId;
    }

    /**
     * Returns the JWS algorithm.
     *
     * @return the algorithm
     */
    public String getAlgorithm()
    {
        return algorithm;
    }

    /**
     * Returns the public key.
     *
     * @return X.509 encoded in Base64
     */
    public String getPublicKey()
    {
        return publicKey;
    }

    /**
     * Returns the encrypted private key.
     *
     * @return the ciphertext
     */
    public String getPrivateKey()
    {
        return privateKey;
    }

    /**
     * Returns when the key began signing.
     *
     * @return the time
     */
    public Instant getActivatedAt()
    {
        return activatedAt;
    }

    /**
     * Returns when the key stopped signing.
     *
     * @return the time, or {@code null} while it is the active key
     */
    public @Nullable Instant getRetiredAt()
    {
        return retiredAt;
    }
}

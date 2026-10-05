// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.agent;

import org.bouncycastle.crypto.params.AsymmetricKeyParameter;
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters;
import org.bouncycastle.crypto.signers.Ed25519Signer;
import org.bouncycastle.crypto.util.PublicKeyFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

/**
 * The server's Ed25519 public key that policy snapshots are signed with, as {@code GET /api/v1/agent/signing-key}
 * returns it: X.509-encoded and Base64. Immutable.
 */
public final class SigningKey
{
    private final String encoded;
    private final String keyId;
    private final Ed25519PublicKeyParameters key;

    private SigningKey(String encoded, String keyId, Ed25519PublicKeyParameters key)
    {
        this.encoded = encoded;
        this.keyId = keyId;
        this.key = key;
    }

    /**
     * Reads a public key.
     *
     * @param encoded the key, X.509 SubjectPublicKeyInfo, Base64
     * @return the key
     * @throws IllegalArgumentException if it is not an Ed25519 public key
     */
    public static SigningKey of(String encoded)
    {
        String trimmed = encoded.trim();
        byte[] bytes;
        AsymmetricKeyParameter parameters;
        try {
            bytes = Base64.getDecoder().decode(trimmed);
            parameters = PublicKeyFactory.createKey(bytes);
        }
        catch (IOException | IllegalArgumentException | IllegalStateException broken) {
            throw new IllegalArgumentException("not an X.509 public key", broken);
        }
        if (!(parameters instanceof Ed25519PublicKeyParameters)) {
            throw new IllegalArgumentException("not an Ed25519 public key");
        }
        return new SigningKey(trimmed, keyId(bytes), (Ed25519PublicKeyParameters) parameters);
    }

    /**
     * The id the server gives a key, which snapshots name in their {@code X-GrantForge-Signing-Key} header: the first
     * 16 hexadecimal digits of the SHA-256 of its X.509 form.
     */
    private static String keyId(byte[] encoded)
    {
        byte[] digest;
        try {
            digest = MessageDigest.getInstance("SHA-256").digest(encoded);
        }
        catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is not available", impossible);
        }
        StringBuilder hex = new StringBuilder();
        for (int index = 0; index < 8; index++) {
            hex.append(String.format("%02x", digest[index] & 0xff));
        }
        return hex.toString();
    }

    /**
     * Returns the key as it was given.
     *
     * @return the key, X.509, Base64
     */
    public String encoded()
    {
        return encoded;
    }

    /**
     * Returns the key's id.
     *
     * @return the id
     */
    public String keyId()
    {
        return keyId;
    }

    /**
     * Checks a signature.
     *
     * @param data what was signed
     * @param signature the signature, Base64
     * @return {@code true} if this key made the signature over the data
     */
    public boolean verifies(byte[] data, String signature)
    {
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(signature.trim().getBytes(StandardCharsets.US_ASCII));
        }
        catch (IllegalArgumentException broken) {
            return false;
        }
        Ed25519Signer verifier = new Ed25519Signer();
        verifier.init(false, key);
        verifier.update(data, 0, data.length);
        return verifier.verifySignature(bytes);
    }
}

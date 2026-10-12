// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.mysql.agent.proxy;

import javax.crypto.Cipher;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/**
 * Carries a password across the proxy when caching_sha2_password needs it whole and the two sides differ in TLS. MySQL
 * takes the password in clear on a TLS connection and encrypted with its RSA key on a plain one; a client does the same
 * by what it sees of its own connection. So the proxy plays the server's part toward a plain client, with an RSA key of
 * its own, and the client's part toward a plain server, with the server's key. The JDK's RSA with OAEP padding does the
 * encryption, as MySQL's caching_sha2_password specifies; the password is the scramble's XOR, as the protocol has it.
 */
final class PasswordBridge
{
    private static final String CIPHER = "RSA/ECB/OAEPWithSHA-1AndMGF1Padding";

    private final KeyPair keys;
    private final byte[] publicKey;

    /**
     * Creates the proxy's RSA key.
     *
     * @throws IllegalStateException when the JDK has no RSA
     */
    PasswordBridge()
    {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            keys = generator.generateKeyPair();
        }
        catch (GeneralSecurityException missing) {
            throw new IllegalStateException("RSA is not available", missing);
        }
        String body = Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.US_ASCII)).encodeToString(keys.getPublic().getEncoded());
        publicKey = ("-----BEGIN PUBLIC KEY-----\n" + body + "\n-----END PUBLIC KEY-----\n").getBytes(StandardCharsets.US_ASCII);
    }

    /**
     * Returns the proxy's public key as caching_sha2_password sends it, in PEM.
     *
     * @return the key
     */
    byte[] publicKey()
    {
        return publicKey.clone();
    }

    /**
     * Reads the password a plain client encrypted with the proxy's key.
     *
     * @param encrypted what the client sent
     * @param scramble the server's scramble of this sign-in
     * @return the password with its closing zero byte, as MySQL takes it in clear
     * @throws GeneralSecurityException when it is not encrypted with the proxy's key
     */
    byte[] decrypt(byte[] encrypted, byte[] scramble) throws GeneralSecurityException
    {
        Cipher cipher = Cipher.getInstance(CIPHER);
        cipher.init(Cipher.DECRYPT_MODE, keys.getPrivate());
        return xor(cipher.doFinal(encrypted), scramble);
    }

    /**
     * Encrypts a password with the server's key for a plain connection to it.
     *
     * @param password the password with its closing zero byte, as the client sent it in clear
     * @param scramble the server's scramble of this sign-in
     * @param pem the server's public key in PEM
     * @return what the server takes
     * @throws GeneralSecurityException when the key cannot be read or used
     */
    static byte[] encrypt(byte[] password, byte[] scramble, byte[] pem) throws GeneralSecurityException
    {
        String text = new String(pem, StandardCharsets.US_ASCII).replaceAll("-----[A-Z ]+-----", "").replaceAll("\\s", "");
        PublicKey key = KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(text)));
        Cipher cipher = Cipher.getInstance(CIPHER);
        cipher.init(Cipher.ENCRYPT_MODE, key);
        return cipher.doFinal(xor(password, scramble));
    }

    private static byte[] xor(byte[] bytes, byte[] scramble)
    {
        byte[] mixed = new byte[bytes.length];
        for (int index = 0; index < bytes.length; index++) {
            mixed[index] = (byte) (bytes[index] ^ scramble[index % scramble.length]);
        }
        return mixed;
    }
}

// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.testsupport;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.time.Instant;
import java.util.Locale;

/**
 * Computes the codes an authenticator app shows (RFC 6238: HMAC-SHA1, six digits, thirty seconds), so tests can sign in
 * in two steps as a user with an authenticator would.
 */
public final class TotpCodes
{
    private static final String BASE32 = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";

    private TotpCodes()
    {
    }

    /**
     * Returns the code for a moment.
     *
     * @param secret the secret in Base32, as the server shows it
     * @param at the moment
     * @return six digits
     * @throws IllegalStateException if the JVM has no HmacSHA1, which every Java platform must provide
     */
    public static String code(String secret, Instant at)
    {
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(decode(secret), "HmacSHA1"));
            byte[] hash = mac.doFinal(ByteBuffer.allocate(Long.BYTES).putLong(at.getEpochSecond() / 30).array());
            int offset = hash[hash.length - 1] & 0x0f;
            int binary = (hash[offset] & 0x7f) << 24 | (hash[offset + 1] & 0xff) << 16 | (hash[offset + 2] & 0xff) << 8 | hash[offset + 3] & 0xff;
            return String.format(Locale.ROOT, "%06d", binary % 1_000_000);
        }
        catch (GeneralSecurityException impossible) {
            throw new IllegalStateException("HmacSHA1 is not available", impossible);
        }
    }

    private static byte[] decode(String secret)
    {
        String clean = secret.replace("=", "").replace(" ", "").toUpperCase(Locale.ROOT);
        ByteBuffer bytes = ByteBuffer.allocate(clean.length() * 5 / 8);
        int buffer = 0;
        int bits = 0;
        for (char character : clean.toCharArray()) {
            int value = BASE32.indexOf(character);
            if (value < 0) {
                throw new IllegalArgumentException("not Base32: " + character);
            }
            buffer = buffer << 5 | value;
            bits += 5;
            if (bits >= 8) {
                bytes.put((byte) (buffer >> bits - 8 & 0xff));
                bits -= 8;
            }
        }
        return bytes.array();
    }
}

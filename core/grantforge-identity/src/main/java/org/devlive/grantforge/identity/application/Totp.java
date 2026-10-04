// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.identity.application;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Locale;
import java.util.OptionalLong;

/**
 * Time-based one-time passwords (RFC 6238) as authenticator apps make them: HMAC-SHA1, six digits, 30-second steps. A code
 * of the step before or after counts too, for clocks that drift. Secrets travel as Base32 (RFC 4648), as apps take them.
 */
final class Totp
{
    /** Seconds per step. */
    static final int PERIOD = 30;

    /** Digits of a code. */
    static final int DIGITS = 6;

    private static final String BASE32 = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
    private static final int MODULUS = 1_000_000;

    private Totp()
    {
    }

    /**
     * Returns the step of a moment.
     *
     * @param now the moment
     * @return the step
     */
    static long step(Instant now)
    {
        return Math.floorDiv(now.getEpochSecond(), PERIOD);
    }

    /**
     * Returns the code of a step.
     *
     * @param key the secret
     * @param step the step
     * @return six digits
     */
    static String code(byte[] key, long step)
    {
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(key, "HmacSHA1"));
            byte[] hash = mac.doFinal(ByteBuffer.allocate(Long.BYTES).putLong(step).array());
            int offset = hash[hash.length - 1] & 0x0f;
            int binary = (hash[offset] & 0x7f) << 24 | (hash[offset + 1] & 0xff) << 16 | (hash[offset + 2] & 0xff) << 8 | hash[offset + 3] & 0xff;
            return String.format(Locale.ROOT, "%0" + DIGITS + "d", binary % MODULUS);
        }
        catch (GeneralSecurityException impossible) {
            throw new IllegalStateException("HmacSHA1 is not available", impossible);
        }
    }

    /**
     * Finds the step a code belongs to, among the current one and its neighbours.
     *
     * @param key the secret
     * @param code the code entered
     * @param now the current time
     * @return the step, or empty if the code fits none
     */
    static OptionalLong verify(byte[] key, String code, Instant now)
    {
        String entered = code.replace(" ", "");
        if (entered.length() != DIGITS) {
            return OptionalLong.empty();
        }
        long current = step(now);
        for (long step = current - 1; step <= current + 1; step++) {
            if (MessageDigest.isEqual(code(key, step).getBytes(StandardCharsets.US_ASCII), entered.getBytes(StandardCharsets.US_ASCII))) {
                return OptionalLong.of(step);
            }
        }
        return OptionalLong.empty();
    }

    /**
     * Encodes bytes in Base32 without padding.
     *
     * @param bytes the bytes
     * @return the text
     */
    static String base32(byte[] bytes)
    {
        StringBuilder text = new StringBuilder();
        int buffer = 0;
        int bits = 0;
        for (byte value : bytes) {
            buffer = buffer << 8 | value & 0xff;
            bits += 8;
            while (bits >= 5) {
                text.append(BASE32.charAt(buffer >>> bits - 5 & 31));
                bits -= 5;
            }
        }
        if (bits > 0) {
            text.append(BASE32.charAt(buffer << 5 - bits & 31));
        }
        return text.toString();
    }

    /**
     * Decodes Base32, ignoring padding, spaces and case.
     *
     * @param text the text
     * @return the bytes
     * @throws IllegalArgumentException if the text has a character outside Base32
     */
    static byte[] fromBase32(String text)
    {
        String clean = text.replace("=", "").replace(" ", "").toUpperCase(Locale.ROOT);
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
                bytes.put((byte) (buffer >>> bits - 8));
                bits -= 8;
            }
        }
        return bytes.array();
    }
}

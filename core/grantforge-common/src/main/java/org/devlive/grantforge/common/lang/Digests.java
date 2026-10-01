// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.common.lang;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

import static java.util.Objects.requireNonNull;

/** Message digests used for tokens and legacy data. Not for new passwords: those use a password hash. */
public final class Digests
{
    private Digests()
    {
    }

    /**
     * Returns the SHA-256 digest of bytes.
     *
     * @param input the bytes; must not be {@code null}
     * @return the 32-byte digest
     * @throws IllegalStateException if the platform lacks SHA-256, which the Java specification forbids
     */
    public static byte[] sha256(byte[] input)
    {
        requireNonNull(input, "input");
        try {
            return MessageDigest.getInstance("SHA-256").digest(input);
        }
        catch (NoSuchAlgorithmException impossible) {
            // Every Java platform must support SHA-256.
            throw new IllegalStateException("SHA-256 is not available", impossible);
        }
    }

    /**
     * Returns the lowercase hex SHA-256 digest of a string's UTF-8 bytes.
     *
     * @param input the text; must not be {@code null}
     * @return 64 lowercase hex characters
     */
    public static String sha256Hex(String input)
    {
        return HexFormat.of().formatHex(sha256(requireNonNull(input, "input").getBytes(StandardCharsets.UTF_8)));
    }
}

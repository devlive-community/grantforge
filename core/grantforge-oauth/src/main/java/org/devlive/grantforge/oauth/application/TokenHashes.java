// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.oauth.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * How tokens are stored: as SHA-256 hashes. Tokens are long random or signed values, so a fast hash is enough; it lets a
 * presented token be found by an index lookup.
 */
final class TokenHashes
{
    /**
     * Marks a token value an authorization was rebuilt without: only the hash is known. Saving such a value keeps its hash.
     */
    static final String UNKNOWN = "gf-hash:";

    private TokenHashes()
    {
    }

    /**
     * Returns the hash a token is stored as.
     *
     * @param value the token, or a placeholder made by {@link #placeholder}
     * @return the hash, 64 hexadecimal digits
     */
    static String of(String value)
    {
        if (value.startsWith(UNKNOWN)) {
            return value.substring(UNKNOWN.length());
        }
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        }
        catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is not available", impossible);
        }
    }

    /**
     * Stands in for a token whose value is not known.
     *
     * @param hash the token's hash
     * @return a value no client can present
     */
    static String placeholder(String hash)
    {
        return UNKNOWN + hash;
    }
}

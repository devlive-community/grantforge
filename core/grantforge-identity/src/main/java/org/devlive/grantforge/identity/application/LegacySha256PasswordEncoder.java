// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.identity.application;

import org.devlive.grantforge.common.lang.Digests;
import org.jspecify.annotations.Nullable;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Locale;

import static java.util.Objects.requireNonNull;

/**
 * Verifies passwords imported from the pre-rebuild server, which stored an unsalted SHA-256 hex digest
 * (registered as {@code {sha256-legacy}}). It never creates such hashes: a successful sign-in replaces the
 * legacy hash with the current default algorithm.
 */
public final class LegacySha256PasswordEncoder
        implements PasswordEncoder
{
    private final Charset charset;

    /**
     * Creates the encoder.
     *
     * @param charset the character set the old server used to turn passwords into bytes
     */
    public LegacySha256PasswordEncoder(Charset charset)
    {
        this.charset = requireNonNull(charset, "charset");
    }

    /**
     * Always fails: new passwords must use the current algorithm.
     *
     * @param rawPassword ignored
     * @return never returns
     * @throws UnsupportedOperationException always
     */
    @Override
    public String encode(@Nullable CharSequence rawPassword)
    {
        throw new UnsupportedOperationException("legacy SHA-256 hashes are only verified, never created");
    }

    /**
     * Compares the digest of a raw password with a stored legacy digest in constant time.
     *
     * @param rawPassword the password to check; {@code null} never matches
     * @param encodedPassword the stored hex digest (any case); {@code null} never matches
     * @return whether the password matches
     */
    @Override
    public boolean matches(@Nullable CharSequence rawPassword, @Nullable String encodedPassword)
    {
        if (rawPassword == null || encodedPassword == null) {
            return false;
        }
        byte[] digest = Digests.sha256(rawPassword.toString().getBytes(charset));
        byte[] expected = HexFormat.of().formatHex(digest).getBytes(StandardCharsets.US_ASCII);
        byte[] actual = encodedPassword.strip().toLowerCase(Locale.ROOT).getBytes(StandardCharsets.US_ASCII);
        return MessageDigest.isEqual(expected, actual);
    }

    /**
     * Legacy hashes are always replaced by the current algorithm after a successful check.
     *
     * @param encodedPassword the stored digest
     * @return {@code true}
     */
    @Override
    public boolean upgradeEncoding(@Nullable String encodedPassword)
    {
        return true;
    }
}

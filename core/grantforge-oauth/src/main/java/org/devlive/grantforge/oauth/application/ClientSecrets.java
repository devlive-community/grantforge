// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.oauth.application;

import org.jspecify.annotations.Nullable;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;

import static java.util.Objects.requireNonNull;

/**
 * Checks client secrets against the current hash and, during a rotation's grace period, the previous one. A registered
 * client carries both hashes joined by {@link #SEPARATOR}, which no hash contains.
 */
public final class ClientSecrets
        implements PasswordEncoder
{
    /** Joins the current and the previous hash. */
    static final String SEPARATOR = "\n";

    private final PasswordEncoder hashes;

    /**
     * Creates the encoder.
     *
     * @param hashes the encoder client secrets are hashed with
     */
    public ClientSecrets(PasswordEncoder hashes)
    {
        this.hashes = requireNonNull(hashes, "hashes");
    }

    /**
     * Joins the hashes a secret may match.
     *
     * @param current the current hash
     * @param previous the previous hash while it still works, or {@code null}
     * @return what a registered client carries as its secret
     */
    static String join(String current, @Nullable String previous)
    {
        return previous == null ? current : current + SEPARATOR + previous;
    }

    @Override
    public @Nullable String encode(@Nullable CharSequence rawPassword)
    {
        return hashes.encode(rawPassword);
    }

    @Override
    public boolean matches(@Nullable CharSequence rawPassword, @Nullable String encodedPassword)
    {
        if (rawPassword == null || encodedPassword == null) {
            return false;
        }
        for (String hash : List.of(encodedPassword.split(SEPARATOR, 2))) {
            if (hashes.matches(rawPassword, hash)) {
                return true;
            }
        }
        return false;
    }

    /** Never: secrets are rehashed only when they are rotated. */
    @Override
    public boolean upgradeEncoding(@Nullable String encodedPassword)
    {
        return false;
    }
}

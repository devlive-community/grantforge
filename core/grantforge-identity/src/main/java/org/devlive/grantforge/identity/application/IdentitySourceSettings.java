// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.identity.application;

import org.devlive.grantforge.identity.domain.IdentitySource;
import org.devlive.grantforge.identity.domain.IdentitySourceType;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import static java.util.Objects.requireNonNull;

/** Reads and writes the settings and the secret of identity sources. */
@Component
public final class IdentitySourceSettings
{
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final SecretBox secrets;

    /**
     * Creates the helper.
     *
     * @param secrets seals and opens the secrets
     */
    public IdentitySourceSettings(SecretBox secrets)
    {
        this.secrets = requireNonNull(secrets, "secrets");
    }

    /**
     * Returns the settings of a directory.
     *
     * @param source the source, of type LDAP
     * @return the settings
     */
    public LdapSettings ldap(IdentitySource source)
    {
        requireType(source, IdentitySourceType.LDAP);
        return JSON.readValue(source.getSettings(), LdapSettings.class);
    }

    /**
     * Returns the settings of a provider.
     *
     * @param source the source, of type OIDC
     * @return the settings
     */
    public OidcSettings oidc(IdentitySource source)
    {
        requireType(source, IdentitySourceType.OIDC);
        return JSON.readValue(source.getSettings(), OidcSettings.class);
    }

    /**
     * Returns the source's secret in plain text.
     *
     * @param source the source
     * @return the bind password or client secret, or {@code null}
     */
    public @Nullable String secret(IdentitySource source)
    {
        String sealed = source.getSecret();
        return sealed == null ? null : secrets.open(sealed);
    }

    /**
     * Seals a secret for storing.
     *
     * @param plain the secret
     * @return the sealed secret
     */
    String seal(String plain)
    {
        return secrets.seal(plain);
    }

    /**
     * Writes settings as JSON.
     *
     * @param settings the settings record
     * @return JSON
     */
    static String write(Object settings)
    {
        return JSON.writeValueAsString(settings);
    }

    private static void requireType(IdentitySource source, IdentitySourceType type)
    {
        if (source.getType() != type) {
            throw new IllegalArgumentException("source " + source.getCode() + " is not of type " + type);
        }
    }
}

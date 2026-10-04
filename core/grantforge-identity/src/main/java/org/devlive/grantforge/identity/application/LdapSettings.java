// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.identity.application;

import org.devlive.grantforge.common.error.GrantForgeException;
import org.devlive.grantforge.common.lang.Strings;
import org.jspecify.annotations.Nullable;

import java.net.URI;
import java.util.Locale;

/**
 * How to find and sign in users of an LDAP directory (OpenLDAP, Active Directory). Blank values take the defaults,
 * which suit OpenLDAP; for Active Directory use {@code sAMAccountName} and {@code objectGUID}.
 *
 * @param url {@code ldap://} or {@code ldaps://} URLs, separated by spaces for fail-over
 * @param baseDn where users are searched, such as {@code ou=people,dc=example,dc=com}
 * @param bindDn the account that searches, or {@code null} to search anonymously; its password is the source's secret
 * @param userFilter the filter that finds a user, with {@code {0}} for the name entered
 * @param usernameAttribute the attribute that becomes the account's user name
 * @param displayNameAttribute the attribute that becomes the display name
 * @param emailAttribute the attribute that becomes the e-mail address
 * @param idAttribute the attribute that identifies a user across renames (binary values, as {@code objectGUID}, are
 *         stored in hex)
 * @param disableMissing whether a sync disables accounts whose users are no longer found
 */
public record LdapSettings(
        String url,
        String baseDn,
        @Nullable String bindDn,
        String userFilter,
        String usernameAttribute,
        String displayNameAttribute,
        String emailAttribute,
        String idAttribute,
        boolean disableMissing)
{
    /** The filter for OpenLDAP's inetOrgPerson users. */
    public static final String DEFAULT_FILTER = "(&(objectClass=person)(uid={0}))";

    /**
     * Applies the defaults and checks the values.
     *
     * @throws GrantForgeException with {@link IdentityErrorCode#IDENTITY_SOURCE_INVALID}
     */
    public LdapSettings
    {
        url = required(url, "url");
        for (String each : url.split("\\s+")) {
            if (!scheme(each).matches("ldaps?")) {
                throw invalid("url must start with ldap:// or ldaps://");
            }
        }
        baseDn = required(baseDn, "baseDn");
        bindDn = Strings.blankToNull(bindDn);
        userFilter = or(userFilter, DEFAULT_FILTER);
        if (!userFilter.startsWith("(") || !userFilter.contains("{0}")) {
            throw invalid("userFilter must be an LDAP filter with {0} for the user name");
        }
        usernameAttribute = or(usernameAttribute, "uid");
        displayNameAttribute = or(displayNameAttribute, "cn");
        emailAttribute = or(emailAttribute, "mail");
        idAttribute = or(idAttribute, "entryUUID");
    }

    /**
     * Returns settings with the defaults for everything but where the directory is.
     *
     * @param url the URLs
     * @param baseDn the base
     * @return settings with the defaults
     */
    public static LdapSettings of(String url, String baseDn)
    {
        return new LdapSettings(url, baseDn, null, "", "", "", "", "", false);
    }

    private static String scheme(String url)
    {
        try {
            String scheme = URI.create(url).getScheme();
            return scheme == null ? "" : scheme.toLowerCase(Locale.ROOT);
        }
        catch (IllegalArgumentException malformed) {
            return "";
        }
    }

    private static String required(@Nullable String value, String name)
    {
        String text = Strings.blankToNull(value);
        if (text == null) {
            throw invalid(name + " is required");
        }
        return text;
    }

    private static String or(@Nullable String value, String fallback)
    {
        String text = Strings.blankToNull(value);
        return text == null ? fallback : text;
    }

    static GrantForgeException invalid(String reason)
    {
        return new GrantForgeException(IdentityErrorCode.IDENTITY_SOURCE_INVALID, "invalid identity source: " + reason, reason);
    }
}

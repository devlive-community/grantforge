// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.identity;

import jakarta.validation.constraints.Size;
import org.devlive.grantforge.identity.application.LdapSettings;
import org.jspecify.annotations.Nullable;

/**
 * Directory settings as entered; blank values take the defaults of {@link LdapSettings}.
 *
 * @param url {@code ldap://} or {@code ldaps://} URLs, separated by spaces
 * @param baseDn where users are searched
 * @param bindDn the account that searches, or blank to search anonymously
 * @param userFilter the filter that finds a user, with {@code {0}} for the name entered
 * @param usernameAttribute the attribute of the user name
 * @param displayNameAttribute the attribute of the display name
 * @param emailAttribute the attribute of the e-mail address
 * @param idAttribute the attribute that identifies a user across renames
 * @param disableMissing whether a sync disables accounts of users no longer found
 */
public record LdapSettingsRequest(
        @Size(max = 1024) @Nullable String url,
        @Size(max = 512) @Nullable String baseDn,
        @Size(max = 512) @Nullable String bindDn,
        @Size(max = 1024) @Nullable String userFilter,
        @Size(max = 64) @Nullable String usernameAttribute,
        @Size(max = 64) @Nullable String displayNameAttribute,
        @Size(max = 64) @Nullable String emailAttribute,
        @Size(max = 64) @Nullable String idAttribute,
        @Nullable Boolean disableMissing)
{
    /**
     * Checks the settings and applies the defaults.
     *
     * @return the settings
     * @throws org.devlive.grantforge.common.error.GrantForgeException if they cannot work
     */
    @SuppressWarnings("NullAway") // LdapSettings treats missing values as blank ones
    public LdapSettings settings()
    {
        return new LdapSettings(url, baseDn, bindDn, userFilter, usernameAttribute, displayNameAttribute, emailAttribute, idAttribute,
                Boolean.TRUE.equals(disableMissing));
    }
}

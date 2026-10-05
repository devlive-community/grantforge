// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.identity.domain;

/** Where accounts of an identity source sign in. */
public enum IdentitySourceType
{
    /** An LDAP directory such as OpenLDAP or Active Directory: the password is checked by binding as the user. */
    LDAP,

    /** An OpenID Connect provider: the user signs in there and comes back with an ID token. */
    OIDC
}

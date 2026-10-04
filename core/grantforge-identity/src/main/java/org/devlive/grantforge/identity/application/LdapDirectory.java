// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.identity.application;

import org.devlive.grantforge.common.error.GrantForgeException;
import org.devlive.grantforge.common.lang.Strings;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

import javax.naming.AuthenticationException;
import javax.naming.Context;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.SizeLimitExceededException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.SearchControls;
import javax.naming.directory.SearchResult;
import javax.naming.ldap.Control;
import javax.naming.ldap.InitialLdapContext;
import javax.naming.ldap.LdapContext;
import javax.naming.ldap.PagedResultsControl;
import javax.naming.ldap.PagedResultsResponseControl;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * Talks to LDAP directories through JNDI (D-72): it searches users with the configured account (or anonymously) and
 * checks a password by binding as the user found. A wrong password is an empty answer; a directory that cannot be
 * reached or refuses the configured account is {@link IdentityErrorCode#IDENTITY_SOURCE_UNAVAILABLE}.
 *
 * <p>JNDI turns entries into Java objects only when a search asks for objects ({@code returningObjFlag}); the searches
 * here never do and return only the four configured text attributes, so a directory cannot make the server deserialize
 * anything. That is why {@code BanJNDI} is suppressed.
 */
@Component
@SuppressWarnings("BanJNDI")
public class LdapDirectory
{
    /** The most users a sync reads, so a wrong filter cannot exhaust the memory. */
    static final int MAX_USERS = 50_000;

    private static final int PAGE_SIZE = 500;

    /** Identifier attributes JNDI must read as bytes: Active Directory's GUID and security identifier. */
    private static final Set<String> BINARY = Set.of("objectguid", "objectsid");

    /**
     * Finds a user by the name entered and checks the password by binding as them.
     *
     * @param settings the directory
     * @param bindPassword the password of the searching account, if any
     * @param username the name entered
     * @param password the password entered; blank never matches, as a blank bind would be anonymous
     * @return the user, if the name and password are right
     */
    public Optional<DirectoryUser> authenticate(LdapSettings settings, @Nullable String bindPassword, String username,
            @Nullable String password)
    {
        if (password == null || password.isEmpty()) {
            return Optional.empty();
        }
        Optional<Found> found = search(settings, bindPassword, username);
        if (found.isEmpty()) {
            return Optional.empty();
        }
        Found user = found.orElseThrow();
        try {
            connect(settings, user.dn(), password, null).close();
            return Optional.of(user.user());
        }
        catch (AuthenticationException wrong) {
            return Optional.empty();
        }
        catch (NamingException failed) {
            throw unavailable(failed);
        }
    }

    /**
     * Finds a user by name.
     *
     * @param settings the directory
     * @param bindPassword the password of the searching account, if any
     * @param username the name
     * @return the user, if exactly one matches
     */
    public Optional<DirectoryUser> find(LdapSettings settings, @Nullable String bindPassword, String username)
    {
        return search(settings, bindPassword, username).map(Found::user);
    }

    /**
     * Lists the users the filter matches with any name, for a sync.
     *
     * @param settings the directory
     * @param bindPassword the password of the searching account, if any
     * @return the users, at most {@link #MAX_USERS}
     */
    public List<DirectoryUser> list(LdapSettings settings, @Nullable String bindPassword)
    {
        List<DirectoryUser> users = new ArrayList<>();
        String filter = settings.userFilter().replace("{0}", "*");
        try {
            LdapContext context = connect(settings, settings.bindDn(), bindPassword,
                    new Control[] {new PagedResultsControl(PAGE_SIZE, Control.NONCRITICAL)});
            try {
                byte[] cookie;
                do {
                    NamingEnumeration<SearchResult> results = context.search(settings.baseDn(), filter, controls(settings, 0));
                    while (results.hasMore() && users.size() < MAX_USERS) {
                        userOf(settings, results.next()).ifPresent(users::add);
                    }
                    cookie = cookie(context.getResponseControls());
                    if (cookie != null) {
                        context.setRequestControls(new Control[] {new PagedResultsControl(PAGE_SIZE, cookie, Control.CRITICAL)});
                    }
                }
                while (cookie != null && cookie.length > 0 && users.size() < MAX_USERS);
            }
            finally {
                context.close();
            }
        }
        catch (NamingException | IOException failed) {
            throw unavailable(failed);
        }
        return users;
    }

    /**
     * Checks that the directory answers with the configured account, as before saving its settings.
     *
     * @param settings the directory
     * @param bindPassword the password of the searching account, if any
     * @throws GrantForgeException with {@link IdentityErrorCode#IDENTITY_SOURCE_UNAVAILABLE} if it does not
     */
    public void test(LdapSettings settings, @Nullable String bindPassword)
    {
        try {
            LdapContext context = connect(settings, settings.bindDn(), bindPassword, null);
            try {
                // Reads the base entry only, which proves the account may search there.
                SearchControls base = controls(settings, 1);
                base.setSearchScope(SearchControls.OBJECT_SCOPE);
                NamingEnumeration<SearchResult> results = context.search(settings.baseDn(), "(objectClass=*)", base);
                if (!results.hasMore()) {
                    throw new NamingException("base " + settings.baseDn() + " not found");
                }
                results.close();
            }
            finally {
                context.close();
            }
        }
        catch (NamingException failed) {
            throw unavailable(failed);
        }
    }

    private Optional<Found> search(LdapSettings settings, @Nullable String bindPassword, String username)
    {
        try {
            LdapContext context = connect(settings, settings.bindDn(), bindPassword, null);
            try {
                // The name is a filter argument, so JNDI escapes it: a name cannot widen the filter.
                NamingEnumeration<SearchResult> results = context.search(settings.baseDn(), settings.userFilter(), new Object[] {username},
                        controls(settings, 2));
                List<SearchResult> found = new ArrayList<>();
                try {
                    while (results.hasMore()) {
                        found.add(results.next());
                    }
                }
                catch (SizeLimitExceededException ambiguous) {
                    // More than one user has the name: sign in nobody.
                    return Optional.empty();
                }
                if (found.size() != 1) {
                    return Optional.empty();
                }
                SearchResult result = found.get(0);
                return userOf(settings, result).map(user -> new Found(result.getNameInNamespace(), user));
            }
            finally {
                context.close();
            }
        }
        catch (NamingException failed) {
            throw unavailable(failed);
        }
    }

    private static LdapContext connect(LdapSettings settings, @Nullable String dn, @Nullable String password, Control @Nullable [] controls)
            throws NamingException
    {
        Hashtable<String, Object> environment = new Hashtable<>();
        environment.put(Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.ldap.LdapCtxFactory");
        environment.put(Context.PROVIDER_URL, settings.url());
        environment.put(Context.REFERRAL, "ignore");
        environment.put("com.sun.jndi.ldap.connect.timeout", "5000");
        environment.put("com.sun.jndi.ldap.read.timeout", "10000");
        if (BINARY.contains(settings.idAttribute().toLowerCase(Locale.ROOT))) {
            // JNDI would decode these octet strings as text; they are read as bytes and stored in hex.
            environment.put("java.naming.ldap.attributes.binary", settings.idAttribute());
        }
        if (dn == null) {
            environment.put(Context.SECURITY_AUTHENTICATION, "none");
        }
        else {
            environment.put(Context.SECURITY_AUTHENTICATION, "simple");
            environment.put(Context.SECURITY_PRINCIPAL, dn);
            environment.put(Context.SECURITY_CREDENTIALS, password == null ? "" : password);
        }
        return new InitialLdapContext(environment, controls);
    }

    private static SearchControls controls(LdapSettings settings, long limit)
    {
        SearchControls controls = new SearchControls();
        controls.setSearchScope(SearchControls.SUBTREE_SCOPE);
        controls.setCountLimit(limit);
        controls.setTimeLimit(10_000);
        controls.setReturningAttributes(new String[] {settings.usernameAttribute(), settings.displayNameAttribute(), settings.emailAttribute(),
                settings.idAttribute()});
        return controls;
    }

    private static Optional<DirectoryUser> userOf(LdapSettings settings, SearchResult result) throws NamingException
    {
        Attributes attributes = result.getAttributes();
        String username = text(attributes.get(settings.usernameAttribute()));
        String id = text(attributes.get(settings.idAttribute()));
        if (username == null || id == null) {
            return Optional.empty();
        }
        return Optional.of(new DirectoryUser(id, username, text(attributes.get(settings.displayNameAttribute())),
                text(attributes.get(settings.emailAttribute()))));
    }

    private static @Nullable String text(@Nullable Attribute attribute) throws NamingException
    {
        Object value = attribute == null ? null : attribute.get();
        if (value instanceof byte[] bytes) {
            return HexFormat.of().formatHex(bytes);
        }
        return value == null ? null : Strings.blankToNull(value.toString());
    }

    private static byte @Nullable [] cookie(Control @Nullable [] controls)
    {
        if (controls != null) {
            for (Control control : controls) {
                if (control instanceof PagedResultsResponseControl paged) {
                    return paged.getCookie();
                }
            }
        }
        return null;
    }

    private static GrantForgeException unavailable(Exception failed)
    {
        String reason = String.valueOf(failed.getMessage());
        return new GrantForgeException(IdentityErrorCode.IDENTITY_SOURCE_UNAVAILABLE, "directory failed: " + reason, failed, reason);
    }

    /** A user with the entry that holds them. */
    private record Found(String dn, DirectoryUser user)
    {
    }
}

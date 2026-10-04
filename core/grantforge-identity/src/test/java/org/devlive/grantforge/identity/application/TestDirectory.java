// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.identity.application;

import com.unboundid.ldap.listener.InMemoryDirectoryServer;
import com.unboundid.ldap.listener.InMemoryDirectoryServerConfig;
import com.unboundid.ldap.listener.InMemoryListenerConfig;
import com.unboundid.ldap.sdk.OperationType;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.EnumSet;

/**
 * An LDAP directory in memory for tests: {@code dc=example,dc=com} with users below {@code ou=people}, searched by
 * {@value #BIND_DN} with password {@value #BIND_PASSWORD}.
 */
final class TestDirectory
        implements AutoCloseable
{
    static final String BASE = "ou=people,dc=example,dc=com";
    static final String BIND_DN = "cn=reader,dc=example,dc=com";
    static final String BIND_PASSWORD = "reader-secret";

    private final InMemoryDirectoryServer server;

    private TestDirectory(InMemoryDirectoryServer server)
    {
        this.server = server;
    }

    /**
     * Starts a directory with the base entries and the searching account.
     *
     * @return the running directory
     * @throws Exception if it cannot start
     */
    static TestDirectory start() throws Exception
    {
        InMemoryDirectoryServerConfig config = new InMemoryDirectoryServerConfig("dc=example,dc=com");
        config.setListenerConfigs(InMemoryListenerConfig.createLDAPConfig("ldap", 0));
        config.addAdditionalBindCredentials(BIND_DN, BIND_PASSWORD);
        // No schema, so entries may carry Active Directory's objectGUID too.
        config.setSchema(null);
        // Anonymous searches are refused, as most directories are set up.
        config.setAuthenticationRequiredOperationTypes(EnumSet.of(OperationType.SEARCH));
        InMemoryDirectoryServer server = new InMemoryDirectoryServer(config);
        server.add("dn: dc=example,dc=com", "objectClass: top", "objectClass: domain", "dc: example");
        server.add("dn: " + BASE, "objectClass: top", "objectClass: organizationalUnit", "ou: people");
        server.startListening();
        return new TestDirectory(server);
    }

    /**
     * Adds a user.
     *
     * @param uid the user name
     * @param name the display name
     * @param password the password
     * @return the directory
     * @throws Exception if the entry is refused
     */
    TestDirectory user(String uid, String name, String password) throws Exception
    {
        server.add("dn: uid=" + uid + "," + BASE, "objectClass: top", "objectClass: person", "objectClass: organizationalPerson",
                "objectClass: inetOrgPerson", "uid: " + uid, "cn: " + name, "sn: " + name, "mail: " + uid + "@example.com",
                "userPassword: " + password, "objectGUID:: " + Base64.getEncoder().encodeToString(uid.getBytes(StandardCharsets.UTF_8)));
        return this;
    }

    /**
     * Removes a user.
     *
     * @param uid the user name
     * @throws Exception if there is no such user
     */
    void remove(String uid) throws Exception
    {
        server.delete("uid=" + uid + "," + BASE);
    }

    /**
     * Renames a user's display name.
     *
     * @param uid the user name
     * @param name the new display name
     * @throws Exception if there is no such user
     */
    void rename(String uid, String name) throws Exception
    {
        server.modify("dn: uid=" + uid + "," + BASE, "changetype: modify", "replace: cn", "cn: " + name);
    }

    /**
     * Returns the URL clients connect to.
     *
     * @return {@code ldap://localhost:<port>}
     */
    String url()
    {
        return "ldap://localhost:" + server.getListenPort();
    }

    /**
     * Returns settings that find users of this directory by uid.
     *
     * @param disableMissing whether a sync disables accounts of users no longer listed
     * @return the settings
     */
    LdapSettings settings(boolean disableMissing)
    {
        return new LdapSettings(url(), BASE, BIND_DN, "", "", "", "", "", disableMissing);
    }

    @Override
    public void close()
    {
        server.shutDown(true);
    }

    /**
     * Finds a user as the directory client does.
     *
     * @param uid the user name
     * @return the user
     */
    DirectoryUser find(String uid)
    {
        return new LdapDirectory().find(settings(false), BIND_PASSWORD, uid).orElseThrow();
    }
}

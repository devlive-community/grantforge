// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.identity.application;

import org.devlive.grantforge.common.error.GrantForgeException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LdapDirectoryTest
{
    private static TestDirectory server;

    private final LdapDirectory directory = new LdapDirectory();

    @BeforeAll
    static void start() throws Exception
    {
        server = TestDirectory.start().user("alice", "Alice A", "alice-secret").user("bob", "Bob B", "bob-secret");
        for (int i = 0; i < 1203; i++) {
            server.user("user" + i, "User " + i, "x");
        }
    }

    @AfterAll
    static void stop()
    {
        server.close();
    }

    @Test
    void signsUsersInByBindingAsThem()
    {
        LdapSettings settings = server.settings(false);

        DirectoryUser alice = directory.authenticate(settings, TestDirectory.BIND_PASSWORD, "alice", "alice-secret").orElseThrow();

        assertThat(alice.username()).isEqualTo("alice");
        assertThat(alice.displayName()).isEqualTo("Alice A");
        assertThat(alice.email()).isEqualTo("alice@example.com");
        assertThat(alice.id()).matches("[0-9a-f-]{36}");
        assertThat(directory.authenticate(settings, TestDirectory.BIND_PASSWORD, "alice", "wrong")).isEmpty();
        // A blank password would be an anonymous bind, which proves nothing.
        assertThat(directory.authenticate(settings, TestDirectory.BIND_PASSWORD, "alice", "")).isEmpty();
        assertThat(directory.authenticate(settings, TestDirectory.BIND_PASSWORD, "alice", null)).isEmpty();
        assertThat(directory.authenticate(settings, TestDirectory.BIND_PASSWORD, "nobody", "x")).isEmpty();
        // The name cannot widen the filter.
        assertThat(directory.authenticate(settings, TestDirectory.BIND_PASSWORD, "*", "x")).isEmpty();
        assertThat(directory.find(settings, TestDirectory.BIND_PASSWORD, "bob")).get().extracting(DirectoryUser::displayName).isEqualTo("Bob B");
    }

    @Test
    void readsActiveDirectoryGuidsAsBytes()
    {
        LdapSettings ad = new LdapSettings(server.url(), TestDirectory.BASE, TestDirectory.BIND_DN, "", "", "", "", "objectGUID", false);

        assertThat(directory.find(ad, TestDirectory.BIND_PASSWORD, "bob")).get().extracting(DirectoryUser::id).isEqualTo("626f62");
    }

    @Test
    void ignoresNamesSeveralUsersMatch()
    {
        LdapSettings loose = new LdapSettings(server.url(), TestDirectory.BASE, TestDirectory.BIND_DN, "(&(objectClass=person)(cn=*{0}*))",
                "", "", "", "", false);

        assertThat(directory.find(loose, TestDirectory.BIND_PASSWORD, "User 1")).isEmpty();
        assertThat(directory.find(loose, TestDirectory.BIND_PASSWORD, "Alice")).isPresent();
    }

    @Test
    void listsAllUsersPageByPage()
    {
        List<DirectoryUser> users = directory.list(server.settings(false), TestDirectory.BIND_PASSWORD);

        assertThat(users).hasSize(1205);
        assertThat(users).extracting(DirectoryUser::username).contains("alice", "bob", "user1202");
    }

    @Test
    void reportsDirectoriesThatDoNotAnswerOrRefuseTheAccount()
    {
        directory.test(server.settings(false), TestDirectory.BIND_PASSWORD);

        assertThatThrownBy(() -> directory.test(server.settings(false), "wrong"))
                .satisfies(error -> assertThat(((GrantForgeException) error).getErrorCode()).isEqualTo(IdentityErrorCode.IDENTITY_SOURCE_UNAVAILABLE));
        assertThatThrownBy(() -> directory.test(LdapSettings.of(server.url(), "ou=nowhere,dc=example,dc=com"), null))
                .isInstanceOf(GrantForgeException.class);
        LdapSettings missingBase = new LdapSettings(server.url(), "ou=nowhere,dc=example,dc=com", TestDirectory.BIND_DN, "", "", "", "", "", false);
        assertThatThrownBy(() -> directory.test(missingBase, TestDirectory.BIND_PASSWORD)).isInstanceOf(GrantForgeException.class);
        LdapSettings closed = LdapSettings.of("ldap://localhost:1", TestDirectory.BASE);
        assertThatThrownBy(() -> directory.find(closed, null, "alice"))
                .satisfies(error -> assertThat(((GrantForgeException) error).getErrorCode()).isEqualTo(IdentityErrorCode.IDENTITY_SOURCE_UNAVAILABLE));
        assertThatThrownBy(() -> directory.list(closed, null)).isInstanceOf(GrantForgeException.class);
        assertThatThrownBy(() -> directory.authenticate(closed, null, "alice", "x")).isInstanceOf(GrantForgeException.class);
    }
}

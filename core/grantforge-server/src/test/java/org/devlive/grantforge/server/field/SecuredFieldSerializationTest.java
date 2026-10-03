// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.field;

import com.fasterxml.jackson.annotation.JsonFormat;
import org.devlive.grantforge.persistence.secured.FieldView;
import org.devlive.grantforge.persistence.secured.MaskStrategy;
import org.devlive.grantforge.persistence.secured.SecuredField;
import org.devlive.grantforge.server.security.SessionUser;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SecuredFieldSerializationTest
{
    record Person(String name, @SecuredField(entity = "user", field = "email", name = "E-mail") @Nullable String email,
            @SecuredField(entity = "user", field = "lastLoginAt", name = "Last sign-in") @Nullable Instant lastLoginAt)
    {
    }

    @JsonFormat(shape = JsonFormat.Shape.ARRAY)
    record Row(String name, @SecuredField(entity = "user", field = "email", name = "E-mail") String email)
    {
    }

    /** Not a record: its fields are not looked at. */
    static final class Plain
    {
        public String email = "carol@acme.io";
    }

    private final Map<String, FieldView> views = new HashMap<>();
    private final JsonMapper mapper = JsonMapper.builder().addModule(new SecuredFieldSerialization().securedFieldModule(
            (accountId, entity, field) -> accountId == 7 ? views.getOrDefault(field, FieldView.VISIBLE) : FieldView.VISIBLE)).build();

    @AfterEach
    void signOut()
    {
        SecurityContextHolder.clearContext();
    }

    private static void signIn(Object principal)
    {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(principal, null, List.of()));
    }

    private String write(Object value)
    {
        return mapper.writeValueAsString(value);
    }

    @Test
    void showsSecuredFieldsAsTheSignedInReaderMaySeeThem()
    {
        Person alice = new Person("alice", "alice@acme.io", Instant.parse("2026-10-02T08:00:00Z"));
        String full = "{\"name\":\"alice\",\"email\":\"alice@acme.io\",\"lastLoginAt\":\"2026-10-02T08:00:00Z\"}";
        assertThat(write(alice)).isEqualTo(full);
        signIn(new SessionUser(7, 1, "viewer"));
        assertThat(write(alice)).isEqualTo(full);

        views.put("email", FieldView.masked(MaskStrategy.EMAIL));
        views.put("lastLoginAt", FieldView.HIDDEN);
        assertThat(write(alice)).isEqualTo("{\"name\":\"alice\",\"email\":\"a***@acme.io\"}");
        assertThat(write(List.of(new Person("bob", null, null)))).isEqualTo("[{\"name\":\"bob\",\"email\":null}]");
        views.put("lastLoginAt", FieldView.masked(MaskStrategy.FULL));
        assertThat(write(alice)).isEqualTo("{\"name\":\"alice\",\"email\":\"a***@acme.io\",\"lastLoginAt\":null}");
        assertThat(write(new Row("alice", "alice@acme.io"))).isEqualTo("[\"alice\",\"a***@acme.io\"]");
        views.put("email", FieldView.HIDDEN);
        assertThat(write(new Row("alice", "alice@acme.io"))).isEqualTo("[\"alice\",null]");
        assertThat(write(new Plain())).isEqualTo("{\"email\":\"carol@acme.io\"}");

        // Other readers, and callers that are not signed-in users, see the fields as they are.
        signIn(new SessionUser(8, 1, "other"));
        assertThat(write(alice)).isEqualTo(full);
        signIn("agent");
        assertThat(write(new Row("alice", "alice@acme.io"))).isEqualTo("[\"alice\",\"alice@acme.io\"]");
    }
}

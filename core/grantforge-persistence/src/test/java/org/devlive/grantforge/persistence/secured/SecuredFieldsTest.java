// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.persistence.secured;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

class SecuredFieldsTest
{
    private static final DeclaredField EMAIL = new DeclaredField("user", "email", "E-mail");
    private static final DeclaredField PHONE = new DeclaredField("user", "phone", "Phone");

    private record Person(String name, @SecuredField(entity = "user", field = "email", name = "E-mail") String email,
            Contact contact, Status status)
    {
    }

    private record Contact(@SecuredField(entity = "user", field = "phone", name = "Phone") String phone, List<Contact> others)
    {
    }

    private enum Status
    {
        ACTIVE
    }

    private record Page<T>(List<T> items, long total)
    {
    }

    private record Wrapper<T>(Optional<T> value, Map<String, T[]> byKey)
    {
    }

    private record Clash(@SecuredField(entity = "user", field = "email", name = "Mail") String mail, Person person)
    {
    }

    private record Invalid(@SecuredField(entity = "User", field = "email", name = "E-mail") String mail)
    {
    }

    private record Blank(@SecuredField(entity = "user", field = "email", name = " ") String mail)
    {
    }

    private interface Signatures
    {
        Page<Person> page();

        Wrapper<? extends Contact> wrapper();

        List<Person>[] lists();

        String plain();
    }

    private static Type typeOf(String method) throws NoSuchMethodException
    {
        return Signatures.class.getDeclaredMethod(method).getGenericReturnType();
    }

    @Test
    void findsFieldsInRecordsAndWhatTheyHold() throws Exception
    {
        assertThat(SecuredFields.in(Person.class)).containsExactly(EMAIL, PHONE);
        assertThat(SecuredFields.in(Contact.class)).containsExactly(PHONE);
        assertThat(SecuredFields.in(typeOf("page"))).containsExactly(EMAIL, PHONE);
        assertThat(SecuredFields.in(typeOf("wrapper"))).containsExactly(PHONE);
        assertThat(SecuredFields.in(typeOf("lists"))).containsExactly(EMAIL, PHONE);
        assertThat(SecuredFields.in(Person[].class)).containsExactly(EMAIL, PHONE);
    }

    @Test
    void looksIntoNothingElse() throws Exception
    {
        assertThat(SecuredFields.in(typeOf("plain"))).isEmpty();
        assertThat(SecuredFields.in(Status.class)).isEmpty();
        assertThat(SecuredFields.in(void.class)).isEmpty();
        assertThat(SecuredFields.in(Signatures.class)).isEmpty();
        assertThat(SecuredFields.in(Page.class.getTypeParameters()[0])).isEmpty();
    }

    @Test
    void refusesDeclarationsThatDoNotFit()
    {
        assertThatIllegalStateException().isThrownBy(() -> SecuredFields.in(Clash.class))
                .withMessageContaining("entity:user.email is named both Mail and E-mail");
        assertThatIllegalStateException().isThrownBy(() -> SecuredFields.in(Invalid.class)).withMessageContaining("User.email");
        assertThatIllegalStateException().isThrownBy(() -> SecuredFields.in(Blank.class)).withMessageContaining("invalid");
    }
}

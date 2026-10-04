// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.sdk;

import org.devlive.grantforge.sdk.jpa.TestOrder;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EntityDeclarationsTest
{
    @Test
    void declaresAnnotatedEntitiesWithTheKindsOfTheirFields()
    {
        EntityDeclarations.Entity order = EntityDeclarations.of(List.of(TestOrder.class)).entities().get(0);

        assertThat(order.code()).isEqualTo("order");
        assertThat(order.name()).isEqualTo("Orders");
        assertThat(order.owned()).isTrue();
        assertThat(order.unitBased()).isTrue();
        assertThat(order.fields()).containsExactly(
                new EntityDeclarations.FieldDeclaration("title", "Title", "TEXT", List.of()),
                new EntityDeclarations.FieldDeclaration("status", "Status", "CHOICE", List.of("OPEN", "PAID")),
                new EntityDeclarations.FieldDeclaration("total", "Total", "NUMBER", List.of()),
                new EntityDeclarations.FieldDeclaration("card", "Paid by card", "BOOLEAN", List.of()),
                new EntityDeclarations.FieldDeclaration("createdAt", "Created", "TIME", List.of()));
    }

    @Test
    void refusesClassesItCannotDeclare()
    {
        assertThatThrownBy(() -> EntityDeclarations.of(List.of(String.class))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> EntityDeclarations.of(List.of(Odd.class))).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("tags");
        assertThat(EntityDeclarations.of(List.of(Plain.class)).entities().get(0).owned()).isFalse();
    }

    /** An entity with a field conditions cannot test. */
    @GrantForgeEntity(code = "odd", name = "Odd")
    static class Odd
    {
        @GrantForgeField("Tags")
        private List<String> tags = List.of();
    }

    /** An entity without owner or department. */
    @GrantForgeEntity(code = "plain", name = "Plain")
    static class Plain
    {
    }
}

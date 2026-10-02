// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.persistence.secured;

import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.metamodel.Attribute;
import jakarta.persistence.metamodel.EntityType;
import jakarta.persistence.metamodel.Metamodel;
import jakarta.persistence.metamodel.SingularAttribute;
import org.devlive.grantforge.persistence.tenant.TenantSampleEntity;
import org.devlive.grantforge.persistence.tenant.TenantScopedEntity;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
class SecuredEntitiesTest
{
    @Autowired
    private EntityManagerFactory entityManagerFactory;

    enum Status
    {
        ACTIVE, DISABLED
    }

    @SecuredEntity(code = "audit-event", name = " Audit events ", owner = "actorId", unitFromOwner = true, tenant = "tenantId")
    static class Event
    {
        @FilterableField("Action")
        Status status = Status.ACTIVE;

        @FilterableField("When")
        Instant at = Instant.EPOCH;

        @FilterableField("Count")
        int count;

        @FilterableField("Done")
        boolean done;

        Long actorId = 0L;

        Long tenantId = 0L;
    }

    @SecuredEntity(code = "Bad Code", name = " ", owner = "label", unit = "missing", unitFromOwner = true)
    static class Broken
            extends TenantScopedEntity
    {
        @FilterableField("Tags")
        List<String> tags = List.of();

        @FilterableField("Map")
        Map<String, String> settings = Map.of();

        @FilterableField("Gone")
        String gone = "";

        String label = "";
    }

    @SecuredEntity(code = "audit-event", name = "Again", tenant = "tenantId")
    static class Twin
            extends TenantScopedEntity
    {
        Long tenantId = 0L;
    }

    /** A JPA metamodel with these entity classes and their attributes. */
    private static EntityManagerFactory mapped(Map<Class<?>, Map<String, Class<?>>> entities)
    {
        Set<EntityType<?>> types = new LinkedHashSet<>();
        entities.forEach((type, attributes) -> {
            EntityType<?> entity = mock(EntityType.class);
            when(entity.getJavaType()).thenAnswer(invocation -> type);
            Set<SingularAttribute<?, ?>> singular = new LinkedHashSet<>();
            attributes.forEach((name, javaType) -> {
                SingularAttribute<?, ?> attribute = mock(SingularAttribute.class);
                when(attribute.getName()).thenReturn(name);
                when(attribute.getJavaType()).thenAnswer(invocation -> javaType);
                when(attribute.getPersistentAttributeType()).thenReturn(name.equals("settings")
                        ? Attribute.PersistentAttributeType.ELEMENT_COLLECTION : Attribute.PersistentAttributeType.BASIC);
                singular.add(attribute);
            });
            when(entity.getSingularAttributes()).thenAnswer(invocation -> singular);
            types.add(entity);
        });
        Metamodel metamodel = mock(Metamodel.class);
        when(metamodel.getEntities()).thenReturn(types);
        EntityManagerFactory factory = mock(EntityManagerFactory.class);
        when(factory.getMetamodel()).thenReturn(metamodel);
        return factory;
    }

    @Test
    void readsTheDeclarationsOfTheMappedEntities()
    {
        SecuredEntities entities = SecuredEntities.of(entityManagerFactory);
        SecuredEntityDefinition sample = entities.find("tenant-sample").orElseThrow();
        assertThat(sample).extracting(SecuredEntityDefinition::name, SecuredEntityDefinition::type, SecuredEntityDefinition::tenantScoped,
                SecuredEntityDefinition::owner, SecuredEntityDefinition::tenant).containsExactly("Samples", TenantSampleEntity.class, true,
                null, null);
        assertThat(sample.fields()).containsExactly(new DataField("label", "Label", DataFieldType.TEXT, List.of()));
        assertThat(sample.scopes()).containsExactlyInAnyOrder(DataScope.ALL, DataScope.TENANT, DataScope.CONDITION);
        assertThat(sample.resourceCode()).isEqualTo("entity:tenant-sample");
        assertThat(entities.find(TenantSampleEntity.class)).contains(sample);
        assertThat(entities.find(String.class)).isEmpty();
        assertThat(entities.find("nothing")).isEmpty();
        assertThat(entities.all()).containsExactly(sample);
        assertThat(SecuredEntities.none().all()).isEmpty();
    }

    @Test
    void knowsTheKindsOfFieldsAndWhatRowsBelongTo()
    {
        SecuredEntities entities = SecuredEntities.of(mapped(Map.of(Event.class, Map.of("status", Status.class, "at", Instant.class,
                "count", int.class, "done", boolean.class, "actorId", Long.class, "tenantId", Long.class), String.class, Map.of())));
        SecuredEntityDefinition event = entities.find("audit-event").orElseThrow();
        assertThat(event.name()).isEqualTo("Audit events");
        assertThat(event.fields()).extracting(DataField::code, DataField::type).containsExactly(
                tuple("status", DataFieldType.CHOICE), tuple("at", DataFieldType.TIME),
                tuple("count", DataFieldType.NUMBER), tuple("done", DataFieldType.BOOLEAN));
        assertThat(event.field("status").orElseThrow().choices()).containsExactly("ACTIVE", "DISABLED");
        assertThat(event.field("tenantId")).isEmpty();
        assertThat(event).extracting(SecuredEntityDefinition::owner, SecuredEntityDefinition::unit, SecuredEntityDefinition::unitFromOwner,
                SecuredEntityDefinition::tenantScoped, SecuredEntityDefinition::tenant).containsExactly("actorId", null, true, false, "tenantId");
        assertThat(event.hasUnits()).isTrue();
        assertThat(event.scopes()).containsExactlyInAnyOrder(DataScope.values());
    }

    @Test
    void refusesDeclarationsThatDoNotFitTheMapping()
    {
        EntityManagerFactory factory = mapped(Map.of(Broken.class, Map.of("tags", List.class, "settings", Map.class, "label", String.class),
                Event.class, Map.of("actorId", Long.class, "tenantId", Long.class, "status", Status.class, "at", Instant.class,
                        "count", int.class, "done", boolean.class), Twin.class, Map.of("tenantId", Long.class)));
        assertThatThrownBy(() -> SecuredEntities.of(factory)).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Broken: code 'Bad Code' must be")
                .hasMessageContaining("Broken: the name is blank")
                .hasMessageContaining("Broken: owner attribute 'label' is no Long")
                .hasMessageContaining("Broken: unit attribute 'missing' is not mapped")
                .hasMessageContaining("Broken: unitFromOwner needs an owner and no unit")
                .hasMessageContaining("Broken.tags: List fields cannot be filtered")
                .hasMessageContaining("Broken.settings: a filterable field must be a basic mapped attribute")
                .hasMessageContaining("Broken.gone: a filterable field must be a basic mapped attribute")
                .hasMessageContaining("code 'audit-event' is used twice")
                .hasMessageContaining("Twin: a tenant-scoped entity declares no tenant attribute");
    }
}

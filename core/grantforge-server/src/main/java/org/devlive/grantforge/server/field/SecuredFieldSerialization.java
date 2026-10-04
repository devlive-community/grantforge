// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.field;

import org.devlive.grantforge.persistence.secured.DeclaredField;
import org.devlive.grantforge.persistence.secured.FieldReadMode;
import org.devlive.grantforge.persistence.secured.FieldRules;
import org.devlive.grantforge.persistence.secured.FieldView;
import org.devlive.grantforge.persistence.secured.SecuredField;
import org.devlive.grantforge.server.security.SessionUser;
import org.jspecify.annotations.Nullable;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.BeanDescription;
import tools.jackson.databind.JacksonModule;
import tools.jackson.databind.SerializationConfig;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.module.SimpleModule;
import tools.jackson.databind.ser.BeanPropertyWriter;
import tools.jackson.databind.ser.ValueSerializerModifier;

import java.io.Serial;
import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static java.util.Objects.requireNonNull;

/**
 * Hides and masks the {@link SecuredField}s of responses as the signed-in reader's field policies say. Fields of responses to
 * anyone else, such as agents or anonymous callers, are written as they are; those APIs return no secured fields.
 */
@Configuration(proxyBeanMethods = false)
public class SecuredFieldSerialization
{
    /**
     * Registers the hiding and masking with the application's JSON mapper.
     *
     * @param rules how readers see the fields
     * @return the Jackson module
     */
    @Bean
    public JacksonModule securedFieldModule(FieldRules rules)
    {
        SimpleModule module = new SimpleModule("grantforge-secured-fields");
        module.setSerializerModifier(new Modifier(rules));
        return module;
    }

    /** Puts a guarding writer in place of the writers of secured record components. */
    static final class Modifier
            extends ValueSerializerModifier
    {
        @Serial
        private static final long serialVersionUID = 1L;

        private final transient FieldRules rules;

        Modifier(FieldRules rules)
        {
            this.rules = requireNonNull(rules, "rules");
        }

        @Override
        public List<BeanPropertyWriter> changeProperties(SerializationConfig config, BeanDescription.Supplier description,
                List<BeanPropertyWriter> properties)
        {
            Class<?> type = description.getBeanClass();
            if (!type.isRecord()) {
                return properties;
            }
            Map<String, SecuredField> secured = Arrays.stream(type.getRecordComponents())
                    .filter(component -> component.isAnnotationPresent(SecuredField.class))
                    .collect(Collectors.toMap(RecordComponent::getName, component -> component.getAnnotation(SecuredField.class)));
            if (secured.isEmpty()) {
                return properties;
            }
            return properties.stream().map(writer -> {
                SecuredField field = secured.get(writer.getMember().getName());
                return field == null ? writer : new Guarded(writer, DeclaredField.of(field), rules);
            }).toList();
        }
    }

    /** Writes a secured field as the reader may see it. */
    static final class Guarded
            extends BeanPropertyWriter
    {
        @Serial
        private static final long serialVersionUID = 1L;

        private final DeclaredField field;
        private final transient FieldRules rules;

        Guarded(BeanPropertyWriter base, DeclaredField field, FieldRules rules)
        {
            super(base);
            this.field = requireNonNull(field, "field");
            this.rules = requireNonNull(rules, "rules");
        }

        @Override
        public void serializeAsProperty(Object bean, JsonGenerator generator, SerializationContext context) throws Exception
        {
            FieldView view = view();
            if (view.mode() == FieldReadMode.VISIBLE) {
                super.serializeAsProperty(bean, generator, context);
            }
            else if (view.mode() == FieldReadMode.MASKED) {
                Object shown = view.present(get(bean));
                generator.writeName(getName());
                if (shown == null) {
                    generator.writeNull();
                }
                else {
                    generator.writeString(shown.toString());
                }
            }
        }

        @Override
        public void serializeAsElement(Object bean, JsonGenerator generator, SerializationContext context) throws Exception
        {
            FieldView view = view();
            if (view.mode() == FieldReadMode.VISIBLE) {
                super.serializeAsElement(bean, generator, context);
                return;
            }
            Object shown = view.present(get(bean));
            if (shown == null) {
                generator.writeNull();
            }
            else {
                generator.writeString(shown.toString());
            }
        }

        private FieldView view()
        {
            SessionUser reader = reader();
            return reader == null ? FieldView.VISIBLE : rules.read(reader.accountId(), field.entity(), field.field());
        }

        private static @Nullable SessionUser reader()
        {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            return authentication != null && authentication.getPrincipal() instanceof SessionUser user ? user : null;
        }
    }
}

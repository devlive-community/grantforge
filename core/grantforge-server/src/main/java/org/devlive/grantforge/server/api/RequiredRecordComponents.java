// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.api;

import io.swagger.v3.core.converter.AnnotatedType;
import io.swagger.v3.core.converter.ModelConverter;
import io.swagger.v3.core.converter.ModelConverterContext;
import io.swagger.v3.core.util.Json;
import io.swagger.v3.oas.models.media.Schema;
import org.jspecify.annotations.Nullable;

import java.lang.reflect.RecordComponent;
import java.util.Iterator;
import java.util.List;

/**
 * Marks the record components of API types as required unless they are {@link Nullable}.
 *
 * <p>All production packages are {@code @NullMarked}, so a component without {@code @Nullable} is never
 * {@code null} on the wire. Publishing that in the contract lets the console's generated TypeScript types
 * use non-optional fields instead of forcing a null check on every property.
 */
public class RequiredRecordComponents
        implements ModelConverter
{
    private static final String SCHEMA_REF_PREFIX = "#/components/schemas/";

    @Override
    public @Nullable Schema<?> resolve(AnnotatedType type, ModelConverterContext context, Iterator<ModelConverter> chain)
    {
        if (!chain.hasNext()) {
            return null;
        }
        Schema<?> schema = chain.next().resolve(type, context, chain);
        Class<?> raw = Json.mapper().constructType(type.getType()).getRawClass();
        if (schema == null || !raw.isRecord()) {
            return schema;
        }
        Schema<?> model = definition(schema, context);
        if (model != null) {
            for (RecordComponent component : raw.getRecordComponents()) {
                boolean nullable = component.getAnnotatedType().isAnnotationPresent(Nullable.class);
                List<String> required = model.getRequired();
                if (!nullable && (required == null || !required.contains(component.getName()))) {
                    model.addRequiredItem(component.getName());
                }
            }
        }
        return schema;
    }

    /** Returns the schema holding the properties: the referenced component schema, or the schema itself. */
    private static @Nullable Schema<?> definition(Schema<?> schema, ModelConverterContext context)
    {
        String ref = schema.get$ref();
        if (ref == null) {
            return schema;
        }
        String name = ref.startsWith(SCHEMA_REF_PREFIX) ? ref.substring(SCHEMA_REF_PREFIX.length()) : ref;
        return context.getDefinedModels().get(name);
    }
}

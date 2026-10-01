// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.api;

import io.swagger.v3.core.converter.AnnotatedType;
import io.swagger.v3.core.converter.ModelConverter;
import io.swagger.v3.core.converter.ModelConverterContext;
import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.oas.models.media.Schema;
import jakarta.validation.constraints.NotBlank;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Iterator;
import java.util.Map;

import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class RequiredRecordComponentsTest
{
    record Sample(String name, @Nullable String note, int count, @NotBlank String code)
    {
    }

    static final class Plain
    {
        public @Nullable String value;
    }

    @SuppressWarnings("rawtypes")
    private static Schema schema(Class<?> type)
    {
        ModelConverters converters = new ModelConverters();
        converters.addConverter(new RequiredRecordComponents());
        Map<String, Schema> schemas = converters.readAll(new AnnotatedType(type));
        return requireNonNull(schemas.get(type.getSimpleName()), type.getSimpleName());
    }

    @Test
    void nonNullComponentsBecomeRequiredOnce()
    {
        assertThat(schema(Sample.class).getRequired())
                .containsExactlyInAnyOrder("name", "count", "code");
    }

    @Test
    void classesThatAreNotRecordsAreLeftAlone()
    {
        assertThat(schema(Plain.class).getRequired()).isNull();
    }

    @Test
    void endOfChainResolvesNothing()
    {
        Iterator<ModelConverter> empty = Collections.emptyIterator();

        assertThat(new RequiredRecordComponents().resolve(new AnnotatedType(Sample.class),
                mock(ModelConverterContext.class), empty)).isNull();
    }
}

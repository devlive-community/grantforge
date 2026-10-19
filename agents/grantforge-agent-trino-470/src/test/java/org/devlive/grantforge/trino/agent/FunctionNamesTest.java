// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.trino.agent;

import io.trino.spi.function.SchemaFunctionName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

class FunctionNamesTest
{
    @Test
    void readsTheSchemaAndTheNameOfTheLineItRunsOn()
    {
        SchemaFunctionName function = new SchemaFunctionName("UDF", "Clean");
        assertThat(FunctionNames.schema(function)).isEqualTo("udf");
        assertThat(FunctionNames.function(function)).isEqualTo("clean");
    }

    @Test
    void findsTheFirstNameTheClassHas()
    {
        assertThat(FunctionNames.accessor("absent", "toString").getName()).isEqualTo("toString");
        assertThatIllegalStateException().isThrownBy(() -> FunctionNames.accessor("absent", "missing"))
                .withMessage("Trino's SchemaFunctionName has none of absent, missing");
    }

    @Test
    void namesAnAccessorThatFails() throws Exception
    {
        assertThatIllegalStateException().isThrownBy(() -> FunctionNames.read(Object.class.getMethod("notify"), new Object()))
                .withMessage("cannot read notify of a Trino function name").withCauseInstanceOf(java.lang.reflect.InvocationTargetException.class);
    }
}

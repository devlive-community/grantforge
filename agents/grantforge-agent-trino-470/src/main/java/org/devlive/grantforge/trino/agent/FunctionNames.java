// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.trino.agent;

import io.trino.spi.function.SchemaFunctionName;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * Reads a function's schema and name on every Trino line: {@link SchemaFunctionName} has getters up to Trino 479 and is
 * a record from 480 on, so a jar built against one would fail on the other if it called either directly.
 */
final class FunctionNames
{
    private static final Method SCHEMA = accessor("schemaName", "getSchemaName");
    private static final Method FUNCTION = accessor("functionName", "getFunctionName");

    private FunctionNames()
    {
    }

    /**
     * Finds the first of some public no-argument methods of {@link SchemaFunctionName}, by name.
     *
     * @param names the names, in the order they are looked for
     * @return the method
     * @throws IllegalStateException if it has none of them
     */
    static Method accessor(String... names)
    {
        for (String name : names) {
            for (Method method : SchemaFunctionName.class.getMethods()) {
                if (method.getName().equals(name) && method.getParameterCount() == 0) {
                    return method;
                }
            }
        }
        throw new IllegalStateException("Trino's SchemaFunctionName has none of " + String.join(", ", names));
    }

    /**
     * Returns the schema of a function.
     *
     * @param function the function
     * @return the schema
     */
    static String schema(SchemaFunctionName function)
    {
        return read(SCHEMA, function);
    }

    /**
     * Returns the name of a function.
     *
     * @param function the function
     * @return the name
     */
    static String function(SchemaFunctionName function)
    {
        return read(FUNCTION, function);
    }

    /**
     * Calls an accessor.
     *
     * @param accessor the accessor
     * @param function the function name
     * @return what it returns
     * @throws IllegalStateException if the call fails
     */
    static String read(Method accessor, Object function)
    {
        try {
            return (String) accessor.invoke(function);
        }
        catch (IllegalAccessException | InvocationTargetException broken) {
            throw new IllegalStateException("cannot read " + accessor.getName() + " of a Trino function name", broken);
        }
    }
}

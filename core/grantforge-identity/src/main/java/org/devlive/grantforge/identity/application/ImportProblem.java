// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.identity.application;

import org.devlive.grantforge.common.error.ErrorCode;
import org.jspecify.annotations.Nullable;

import java.util.List;

import static java.util.Objects.requireNonNull;

/**
 * Why one row of an import file cannot be imported; the server turns the code into a localised message.
 *
 * @param row the 1-based record number in the file, the header being record 1
 * @param column the column at fault, if one is
 * @param code the problem
 * @param arguments the values the problem's message names
 */
public record ImportProblem(int row, @Nullable String column, ErrorCode code, List<Object> arguments)
{
    /** Copies the arguments. */
    public ImportProblem
    {
        requireNonNull(code, "code");
        arguments = List.copyOf(requireNonNull(arguments, "arguments"));
    }
}

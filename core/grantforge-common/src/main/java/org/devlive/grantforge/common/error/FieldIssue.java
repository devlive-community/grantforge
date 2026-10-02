// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.common.error;

import java.util.List;

import static java.util.Objects.requireNonNull;

/**
 * What is wrong with one input of a request, so the console can point at it: the input's name and a message key
 * the server localizes, formatted with the arguments.
 *
 * @param field the input's name
 * @param messageKey the key of the message
 * @param arguments the message's arguments
 */
public record FieldIssue(String field, String messageKey, List<Object> arguments)
{
    /** Checks and copies the values. */
    public FieldIssue
    {
        requireNonNull(field, "field");
        requireNonNull(messageKey, "messageKey");
        arguments = List.copyOf(requireNonNull(arguments, "arguments"));
    }

    /**
     * Creates an issue.
     *
     * @param field the input's name
     * @param messageKey the key of the message
     * @param arguments the message's arguments
     * @return the issue
     */
    public static FieldIssue of(String field, String messageKey, Object... arguments)
    {
        return new FieldIssue(field, messageKey, List.of(arguments));
    }
}

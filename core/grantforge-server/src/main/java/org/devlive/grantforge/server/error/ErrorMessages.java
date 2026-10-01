// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.error;

import org.devlive.grantforge.common.error.ErrorCode;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Component;

import java.util.List;

import static java.util.Objects.requireNonNull;
import static java.util.Objects.requireNonNullElse;

/** Turns error codes into text in the request's language, as problem details do, for reports that list problems. */
@Component
public final class ErrorMessages
{
    private final MessageSource messages;

    /**
     * Creates the component.
     *
     * @param messages the message bundles of every module
     */
    public ErrorMessages(MessageSource messages)
    {
        this.messages = requireNonNull(messages, "messages");
    }

    /**
     * Returns the message of an error in the request's language.
     *
     * @param code the error
     * @param arguments the values the message names
     * @return the message, or the error's code when no bundle has it
     */
    public String text(ErrorCode code, List<Object> arguments)
    {
        return requireNonNullElse(messages.getMessage(code.messageKey(), arguments.toArray(), code.code(),
                LocaleContextHolder.getLocale()), code.code());
    }
}

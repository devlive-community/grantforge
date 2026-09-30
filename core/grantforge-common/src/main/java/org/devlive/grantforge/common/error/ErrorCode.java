// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.common.error;

/**
 * A stable, documented error identifier returned to API clients.
 *
 * <p>Clients branch on {@link #code()}, never on message text. The human-readable message is looked up
 * by {@link #messageKey()} in the caller's language, so the server itself only returns codes.
 */
public interface ErrorCode
{
    /**
     * Returns the stable code, for example {@code GF-COMMON-404}.
     *
     * @return the code; never blank
     */
    String code();

    /**
     * Returns the HTTP status that responses for this error use.
     *
     * @return an HTTP status code between 400 and 599
     */
    int httpStatus();

    /**
     * Returns the i18n message key, for example {@code error.common.not-found}.
     *
     * @return the message key; never blank
     */
    String messageKey();
}

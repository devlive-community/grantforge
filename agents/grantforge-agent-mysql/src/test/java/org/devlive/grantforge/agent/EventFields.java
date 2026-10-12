// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.agent;

import org.jspecify.annotations.Nullable;

import java.util.Map;

/** What an access event sends the server, for the proxy's tests. */
public final class EventFields
{
    private EventFields()
    {
    }

    /**
     * Returns an event's fields.
     *
     * @param event the event
     * @return its fields by name, as the server takes them
     */
    public static Map<String, @Nullable Object> of(AccessEvent event)
    {
        return event.fields();
    }
}

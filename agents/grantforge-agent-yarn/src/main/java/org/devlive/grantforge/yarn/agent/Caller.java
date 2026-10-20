// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.yarn.agent;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.TreeSet;

/**
 * Who asks: the short user name YARN gives the caller, the groups Hadoop's group mapping gives the user, and where the
 * request comes from. Behind a proxy, such as YARN's web proxy or a router, the client is the first address the proxies
 * forwarded; otherwise it is the address that connected.
 */
final class Caller
{
    private final String user;
    private final List<String> groups;
    private final @Nullable String clientIp;

    /**
     * Creates a caller.
     *
     * @param user the user's short name
     * @param groups the user's groups
     * @param clientIp the client's address, or {@code null} when unknown
     */
    Caller(String user, Collection<String> groups, @Nullable String clientIp)
    {
        this.user = user;
        this.groups = Collections.unmodifiableList(new ArrayList<>(new TreeSet<>(groups)));
        this.clientIp = clientIp;
    }

    /**
     * Returns the address a request comes from: the first forwarded address, or the one that connected.
     *
     * @param remote the address that connected, or {@code null}
     * @param forwarded the addresses proxies forwarded, the client's first, or {@code null}
     * @return the client's address, or {@code null} when neither is known
     */
    static @Nullable String client(@Nullable String remote, @Nullable List<String> forwarded)
    {
        if (forwarded != null) {
            for (String address : forwarded) {
                String client = address == null ? "" : address.trim();
                if (!client.isEmpty()) {
                    return client;
                }
            }
        }
        String connected = remote == null ? "" : remote.trim();
        return connected.isEmpty() ? null : connected;
    }

    /**
     * Returns the user.
     *
     * @return the short name
     */
    String user()
    {
        return user;
    }

    /**
     * Returns the groups.
     *
     * @return them, sorted
     */
    List<String> groups()
    {
        return groups;
    }

    /**
     * Returns the client's address.
     *
     * @return it, or {@code null}
     */
    @Nullable String clientIp()
    {
        return clientIp;
    }
}

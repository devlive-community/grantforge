// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.hbase.agent;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.TreeSet;

/** Who asks: the short user name HBase gives the connection, the groups Hadoop's group mapping gives the user and the client's address. */
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

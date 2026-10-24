// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.solr.agent;

import inet.ipaddr.IPAddress;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.TreeSet;

/**
 * Who asks: the user Solr's authentication signed in, the roles a JSON web token verified for the user, which count as
 * groups, and where the request comes from. The client is the address that connected; behind proxies the agent trusts,
 * it is the last address of {@code X-Forwarded-For} that no trusted proxy added, so that a client cannot claim an
 * address of its own choosing.
 */
final class Caller
{
    private final String user;
    private final List<String> groups;
    private final @Nullable String clientIp;

    /**
     * Creates a caller.
     *
     * @param user the user
     * @param groups the user's verified roles
     * @param clientIp the client's address, or {@code null} when unknown
     */
    Caller(String user, Collection<String> groups, @Nullable String clientIp)
    {
        this.user = user;
        this.groups = Collections.unmodifiableList(new ArrayList<>(new TreeSet<>(groups)));
        this.clientIp = clientIp;
    }

    /**
     * Returns the address a request comes from.
     *
     * @param remote the address that connected, or {@code null}
     * @param forwarded the {@code X-Forwarded-For} header, or {@code null}
     * @param proxies the proxies whose forwarded addresses are trusted
     * @return the client's address, or {@code null} when it is not known
     */
    static @Nullable String client(@Nullable String remote, @Nullable String forwarded, List<IPAddress> proxies)
    {
        IPAddress connected = remote == null ? null : AddressRange.address(remote);
        if (connected == null) {
            return null;
        }
        if (forwarded == null || !AddressRange.includes(proxies, connected)) {
            return connected.toCanonicalString();
        }
        String[] hops = forwarded.split(",", -1);
        IPAddress client = connected;
        for (int index = hops.length - 1; index >= 0 && AddressRange.includes(proxies, client); index--) {
            IPAddress hop = AddressRange.address(hops[index]);
            if (hop == null) {
                return null;
            }
            client = hop;
        }
        return client.toCanonicalString();
    }

    /**
     * Returns the user.
     *
     * @return the name
     */
    String user()
    {
        return user;
    }

    /**
     * Returns the groups.
     *
     * @return the verified roles, sorted
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

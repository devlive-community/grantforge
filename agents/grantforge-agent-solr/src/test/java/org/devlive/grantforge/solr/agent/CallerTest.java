// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.solr.agent;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class CallerTest
{
    private static final List<inet.ipaddr.IPAddress> PROXIES = AddressRange.parse(List.of("192.0.2.0/24", "198.51.100.7"));

    @Test
    void takesTheAddressThatConnectedWithoutATrustedProxy()
    {
        assertThat(Caller.client("10.1.2.3", null, PROXIES)).isEqualTo("10.1.2.3");
        assertThat(Caller.client("10.1.2.3", "203.0.113.1", PROXIES)).as("a client cannot claim an address").isEqualTo("10.1.2.3");
        assertThat(Caller.client("10.1.2.3", "203.0.113.1", List.of())).isEqualTo("10.1.2.3");
        assertThat(Caller.client("::1", null, PROXIES)).isEqualTo("::1");
    }

    @Test
    void takesTheLastAddressNoTrustedProxyAdded()
    {
        assertThat(Caller.client("192.0.2.5", "203.0.113.1", PROXIES)).isEqualTo("203.0.113.1");
        assertThat(Caller.client("192.0.2.5", "1.1.1.1, 203.0.113.1, 198.51.100.7", PROXIES)).isEqualTo("203.0.113.1");
        assertThat(Caller.client("192.0.2.5", "198.51.100.7", PROXIES)).isEqualTo("198.51.100.7");
        assertThat(Caller.client("192.0.2.5", "not-an-address", PROXIES)).isNull();
    }

    @Test
    void knowsNoAddressThatIsNone()
    {
        assertThat(Caller.client(null, null, PROXIES)).isNull();
        assertThat(Caller.client("solr1.example.com", null, PROXIES)).isNull();
        assertThat(Caller.client("10.0.0.0/8", null, PROXIES)).isNull();
    }

    @Test
    void keepsTheUserAndSortedGroups()
    {
        Caller caller = new Caller("alice", Set.of("b", "a"), "10.0.0.1");
        assertThat(caller.user()).isEqualTo("alice");
        assertThat(caller.groups()).containsExactly("a", "b");
        assertThat(caller.clientIp()).isEqualTo("10.0.0.1");
    }
}

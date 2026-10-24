// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.solr.agent;

import org.devlive.grantforge.policy.engine.AccessRequest;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AddressRangeTest
{
    private final AddressRange range = new AddressRange();

    private static AccessRequest from(Object address)
    {
        return AccessRequest.builder("alice", "query").resource("collection", "sales").context(Map.of(AddressRange.CLIENT_ADDRESS, address)).build();
    }

    @Test
    void holdsForAddressesAndRanges()
    {
        List<String> values = List.of(" 10.1.0.0/16 ", "192.0.2.7", "2001:db8::/32");
        assertThat(range.holds(values, from("10.1.200.3"))).isTrue();
        assertThat(range.holds(values, from("192.0.2.7"))).isTrue();
        assertThat(range.holds(values, from("2001:db8:1::5"))).isTrue();
        assertThat(range.holds(values, from("10.2.0.1"))).isFalse();
        assertThat(range.holds(values, from("192.0.2.8"))).isFalse();
        assertThat(range.holds(List.of("0.0.0.0/0"), from("203.0.113.9"))).isTrue();
        assertThat(range.holds(List.of("0.0.0.0/0"), from("::1"))).as("an IPv4 range holds for no IPv6 client").isFalse();
    }

    @Test
    void holdsForNoUnknownClientAndNoValueThatIsNoAddress()
    {
        assertThat(range.holds(List.of("10.0.0.0/8"), AccessRequest.builder("alice", "query").resource("collection", "sales").build())).isFalse();
        assertThat(range.holds(List.of("10.0.0.0/8"), from("solr.example.com"))).isFalse();
        assertThat(range.holds(List.of("10.0.0.0/8"), from(42))).isFalse();
        assertThat(range.holds(List.of("solr.example.com", "10.0.0.0/33"), from("10.0.0.1"))).isFalse();
        assertThat(AddressRange.parse(List.of("nowhere", "10.0.0.1"))).hasSize(1);
        assertThat(AddressRange.address("10.0.0.0/8")).isNull();
    }

    @Test
    void keepsTheListsItParsedUpToALimit()
    {
        for (int index = 0; index < 1005; index++) {
            assertThat(range.holds(new ArrayList<>(List.of("10.0." + index / 250 + "." + index % 250)), from("192.0.2.1"))).isFalse();
        }
        assertThat(range.holds(List.of("10.0.0.0/8"), from("10.0.0.1"))).isTrue();
        assertThat(range.holds(List.of("10.0.0.0/8"), from("10.0.0.1"))).isTrue();
    }
}

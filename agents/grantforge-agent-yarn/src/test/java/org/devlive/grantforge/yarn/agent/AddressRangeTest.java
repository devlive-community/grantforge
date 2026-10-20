// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.yarn.agent;

import org.devlive.grantforge.policy.engine.AccessRequest;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class AddressRangeTest
{
    private final AddressRange range = new AddressRange();

    private static AccessRequest from(Object address)
    {
        return AccessRequest.builder("alice", "submit_app").resource("queue", "root").context(Collections.singletonMap("clientAddress", address))
                .build();
    }

    @Test
    void holdsForAddressesRangesAndNotOthers()
    {
        List<String> values = List.of("10.1.0.0/16", " 192.168.7.9 ", "");
        assertThat(range.holds(values, from("10.1.200.3"))).isTrue();
        assertThat(range.holds(values, from("192.168.7.9"))).isTrue();
        assertThat(range.holds(values, from("10.2.0.1"))).isFalse();
        assertThat(range.holds(List.of("0.0.0.0/0"), from("203.0.113.9"))).isTrue();
        assertThat(range.holds(List.of("*"), from("203.0.113.9"))).as("Hadoop's wildcard").isTrue();
    }

    @Test
    void holdsForNoClientItDoesNotKnow()
    {
        assertThat(range.holds(List.of("0.0.0.0/0"), AccessRequest.builder("alice", "admin").resource("cluster", "*").build())).isFalse();
        assertThat(range.holds(List.of("0.0.0.0/0"), from(""))).isFalse();
        assertThat(range.holds(List.of("0.0.0.0/0"), from(7))).isFalse();
    }

    @Test
    void keepsAtMostSoManyLists()
    {
        IntStream.range(0, 1100).forEach(index -> assertThat(range.holds(List.of("10." + (index / 250) + "." + (index % 250) + ".0/24"),
                from("172.16.0.1"))).isFalse());
        assertThat(range.holds(List.of("10.9.9.0/24"), from("10.9.9.9"))).isTrue();
    }
}

// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.solr.agent;

import inet.ipaddr.IPAddress;
import inet.ipaddr.IPAddressString;
import org.devlive.grantforge.policy.engine.AccessRequest;
import org.devlive.grantforge.policy.engine.ConditionEvaluator;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * The {@code ip-range} condition: a policy item holds for the clients whose address is one of the condition's values, each
 * an address or a range such as {@code 10.1.0.0/16} or {@code 2001:db8::/32}. A request whose client is not known matches
 * no range, and a value that is no address matches nothing; host names are not looked up.
 */
final class AddressRange
        implements ConditionEvaluator
{
    /** The condition type the Solr service type names. */
    static final String TYPE = "ip-range";

    /** What the agent puts the client's address under in a request. */
    static final String CLIENT_ADDRESS = "clientAddress";

    /** The most lists kept, each the parsed values of one condition. */
    private static final int MAX_LISTS = 1000;

    private static final Logger LOG = LoggerFactory.getLogger(AddressRange.class);

    private final ConcurrentMap<List<String>, List<IPAddress>> lists = new ConcurrentHashMap<>();

    @Override
    public boolean holds(List<String> values, AccessRequest request)
    {
        Object address = request.context().get(CLIENT_ADDRESS);
        IPAddress client = address instanceof String ? address((String) address) : null;
        return client != null && includes(ranges(values), client);
    }

    private List<IPAddress> ranges(List<String> values)
    {
        List<IPAddress> known = lists.get(values);
        if (known != null) {
            return known;
        }
        List<IPAddress> ranges = parse(values);
        if (lists.size() < MAX_LISTS) {
            List<IPAddress> first = lists.putIfAbsent(List.copyOf(values), ranges);
            return first == null ? ranges : first;
        }
        return ranges;
    }

    /**
     * Parses addresses and ranges, leaving out, with a warning, what is neither.
     *
     * @param values such as {@code 10.0.0.0/8} or {@code 192.0.2.7}
     * @return the ranges
     */
    static List<IPAddress> parse(List<String> values)
    {
        List<IPAddress> ranges = new ArrayList<>();
        for (String value : values) {
            String entry = value.strip();
            IPAddress range = range(entry);
            if (range == null) {
                LOG.warn("GrantForge ignores the client address {}: it is no address or range", entry);
            }
            else {
                ranges.add(range.toPrefixBlock());
            }
        }
        return Collections.unmodifiableList(ranges);
    }

    private static @Nullable IPAddress range(String value)
    {
        return new IPAddressString(value).getAddress();
    }

    /**
     * Reads one address.
     *
     * @param value such as {@code 10.0.0.7}
     * @return it, or {@code null} for what is no single address
     */
    static @Nullable IPAddress address(String value)
    {
        IPAddress address = new IPAddressString(value.strip()).getAddress();
        return address == null || address.isPrefixed() ? null : address;
    }

    /**
     * Returns whether an address is in one of some ranges.
     *
     * @param ranges the ranges
     * @param address the address
     * @return {@code true} if it is
     */
    static boolean includes(List<IPAddress> ranges, IPAddress address)
    {
        for (IPAddress range : ranges) {
            if (range.getIPVersion() == address.getIPVersion() && range.contains(address)) {
                return true;
            }
        }
        return false;
    }
}

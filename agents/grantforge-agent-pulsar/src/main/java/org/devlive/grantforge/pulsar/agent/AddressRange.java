// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.pulsar.agent;

import io.netty.handler.ipfilter.IpFilterRuleType;
import io.netty.handler.ipfilter.IpSubnetFilterRule;
import io.netty.util.NetUtil;
import org.devlive.grantforge.policy.engine.AccessRequest;
import org.devlive.grantforge.policy.engine.ConditionEvaluator;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * The {@code ip-range} condition: a policy item holds for the clients whose address is one of the condition's values, each
 * an address or a range such as {@code 10.1.0.0/16} or {@code 2001:db8::/32}, matched by Netty's own subnet rules, which
 * the broker's network stack uses. A request whose client is not known matches no range, and a value that is no address
 * matches nothing; host names are not looked up.
 */
final class AddressRange
        implements ConditionEvaluator
{
    /** The condition type the Pulsar service type names. */
    static final String TYPE = "ip-range";

    /** What the agent puts the client's address under in a request. */
    static final String CLIENT_ADDRESS = "clientAddress";

    /** The most lists kept, each the parsed values of one condition. */
    private static final int MAX_LISTS = 1000;

    private static final Logger LOG = LoggerFactory.getLogger(AddressRange.class);

    private final ConcurrentMap<List<String>, List<IpSubnetFilterRule>> lists = new ConcurrentHashMap<>();

    @Override
    public boolean holds(List<String> values, AccessRequest request)
    {
        Object address = request.context().get(CLIENT_ADDRESS);
        byte[] bytes = address instanceof String text ? NetUtil.createByteArrayFromIpAddressString(text) : null;
        if (bytes == null) {
            return false;
        }
        InetSocketAddress client;
        try {
            client = new InetSocketAddress(InetAddress.getByAddress(bytes), 0);
        }
        catch (UnknownHostException impossible) {
            return false;
        }
        for (IpSubnetFilterRule rule : rules(values)) {
            if (rule.matches(client)) {
                return true;
            }
        }
        return false;
    }

    private List<IpSubnetFilterRule> rules(List<String> values)
    {
        List<IpSubnetFilterRule> known = lists.get(values);
        if (known != null) {
            return known;
        }
        List<IpSubnetFilterRule> parsed = new ArrayList<>();
        for (String value : values) {
            IpSubnetFilterRule rule = rule(value);
            if (rule != null) {
                parsed.add(rule);
            }
        }
        List<IpSubnetFilterRule> rules = List.copyOf(parsed);
        if (lists.size() < MAX_LISTS) {
            List<IpSubnetFilterRule> first = lists.putIfAbsent(List.copyOf(values), rules);
            return first == null ? rules : first;
        }
        return rules;
    }

    /**
     * Parses one value.
     *
     * @param value an address or {@code address/prefix}
     * @return the rule, or {@code null} for a value that is neither
     */
    static @Nullable IpSubnetFilterRule rule(String value)
    {
        String entry = value.strip();
        int slash = entry.indexOf('/');
        String address = slash < 0 ? entry : entry.substring(0, slash);
        byte[] bytes = NetUtil.createByteArrayFromIpAddressString(address);
        if (bytes == null) {
            LOG.warn("GrantForge ignores the client address {}: it is no address or range", entry);
            return null;
        }
        int bits = bytes.length * 8;
        int prefix;
        try {
            prefix = slash < 0 ? bits : Integer.parseInt(entry.substring(slash + 1));
        }
        catch (NumberFormatException wrong) {
            prefix = -1;
        }
        if (prefix < 0 || prefix > bits) {
            LOG.warn("GrantForge ignores the client address range {}: its prefix is not 0 to {}", entry, bits);
            return null;
        }
        return new IpSubnetFilterRule(address, prefix, IpFilterRuleType.ACCEPT);
    }
}

// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.yarn.agent;

import org.apache.hadoop.util.MachineList;
import org.devlive.grantforge.policy.engine.AccessRequest;
import org.devlive.grantforge.policy.engine.ConditionEvaluator;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * The {@code ip-range} condition: a policy item holds for the clients whose address is one of the condition's values,
 * each an address, a range such as {@code 10.1.0.0/16} or a host name, as Hadoop's own host lists read them. A request
 * whose client is not known matches no range. A host name costs a name lookup of the client's address each time a decision
 * is made rather than kept, so addresses and ranges are the better values.
 */
final class AddressRange
        implements ConditionEvaluator
{
    /** The condition type the YARN service type names. */
    static final String TYPE = "ip-range";

    /** What the agent puts the client's address under in a request. */
    static final String CLIENT_ADDRESS = "clientAddress";

    /** The most lists kept, each the parsed values of one condition. */
    private static final int MAX_LISTS = 1000;

    private final ConcurrentMap<List<String>, MachineList> lists = new ConcurrentHashMap<>();

    @Override
    public boolean holds(List<String> values, AccessRequest request)
    {
        Object address = request.context().get(CLIENT_ADDRESS);
        if (!(address instanceof String) || ((String) address).isEmpty()) {
            return false;
        }
        MachineList list = lists.get(values);
        if (list == null) {
            List<String> entries = new ArrayList<>();
            for (String value : values) {
                String entry = value.trim();
                if (!entry.isEmpty()) {
                    entries.add(entry);
                }
            }
            list = new MachineList(entries);
            if (lists.size() < MAX_LISTS) {
                MachineList known = lists.putIfAbsent(new ArrayList<>(values), list);
                list = known == null ? list : known;
            }
        }
        return list.includes((String) address);
    }
}

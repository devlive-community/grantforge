// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.agent;

import org.devlive.grantforge.policy.engine.AccessRequest;

/** Snapshots as the server writes them, of a small Hive-like service. */
final class Snapshots
{
    private Snapshots()
    {
    }

    /**
     * A snapshot: analysts and ops may select in sales; spies may not update there; nobody selects hr salaries from
     * 10.x (an override, through the {@code ip-range} evaluator); an archive policy expired in 2020; one masking policy.
     */
    static String json(long version, boolean enabled)
    {
        return """
                {"format": 1, "service": "warehouse", "serviceType": "hive", "typeVersion": 1, "serviceEnabled": %s,
                 "policyVersion": %d,
                 "definition": {
                   "resources": [
                     {"name": "table", "parent": "database", "matcher": "WILDCARD", "caseSensitive": false},
                     {"name": "column", "parent": "table", "matcher": "WILDCARD", "caseSensitive": false},
                     {"name": "database", "parent": null, "matcher": "WILDCARD", "caseSensitive": false}],
                   "accessTypes": [
                     {"name": "select", "impliedGrants": []},
                     {"name": "update", "impliedGrants": []},
                     {"name": "all", "impliedGrants": ["select", "update"]}],
                   "conditions": [{"name": "ip", "evaluator": "ip-range", "options": {}}],
                   "maskTypes": [{"name": "MASK", "transformer": null}]},
                 "policies": [
                   {"id": "11", "name": "sales", "type": "ACCESS", "priority": "NORMAL", "document": {
                     "resources": {"database": {"values": ["sales"], "excludes": false, "recursive": false},
                                   "table": {"values": ["*"], "excludes": false, "recursive": false},
                                   "column": {"values": ["*"], "excludes": false, "recursive": false}},
                     "allow": [{"users": [], "groups": ["ops"], "roles": ["analyst"], "accessTypes": ["select"], "conditions": [],
                                "maskType": null, "maskValue": null, "rowFilter": null}],
                     "allowExceptions": [],
                     "deny": [{"users": [], "groups": ["spies"], "roles": [], "accessTypes": ["update"], "conditions": []}],
                     "denyExceptions": [], "validity": []}},
                   {"id": "12", "name": "no salaries from 10.x", "type": "ACCESS", "priority": "OVERRIDE", "document": {
                     "resources": {"database": {"values": ["hr"], "excludes": false, "recursive": false},
                                   "table": {"values": ["salaries"], "excludes": false, "recursive": false},
                                   "column": {"values": ["*"], "excludes": false, "recursive": false}},
                     "allow": [{"users": ["carol"], "groups": [], "roles": [], "accessTypes": ["all"]}],
                     "deny": [{"users": [], "groups": ["public"], "roles": [], "accessTypes": ["select"],
                               "conditions": [{"type": "ip", "values": ["10.0.0.0/8"]}]}]}},
                   {"id": "13", "name": "mask ssn", "type": "DATA_MASK", "priority": "NORMAL", "document": {
                     "resources": {"database": {"values": ["sales"], "excludes": false, "recursive": false}},
                     "allow": [{"users": [], "groups": ["public"], "roles": [], "accessTypes": ["select"], "maskType": "MASK"}]}},
                   {"id": "14", "name": "archive", "type": "ACCESS", "priority": "NORMAL", "document": {
                     "resources": {"database": {"values": ["archive"], "excludes": false, "recursive": false},
                                   "table": {"values": ["*"], "excludes": false, "recursive": false},
                                   "column": {"values": ["*"], "excludes": false, "recursive": false}},
                     "allow": [{"users": ["dave"], "groups": [], "roles": [], "accessTypes": ["select"]}],
                     "validity": [{"from": null, "until": "2020-01-01T00:00:00Z"}]}}],
                 "roles": {"analyst": ["alice"]},
                 "groups": {"ops": ["bob"], "spies": ["eve", "bob"]}}
                """.formatted(enabled, version);
    }

    /** A request for a column. */
    static AccessRequest request(String user, String accessType, String database, String table)
    {
        return AccessRequest.builder(user, accessType).resource("database", database).resource("table", table).resource("column", "id")
                .build();
    }
}

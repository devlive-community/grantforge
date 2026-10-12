// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.agent;

import org.devlive.grantforge.policy.engine.AccessRequest;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

/** Masking and row filtering policies of a MySQL-like service, as the server writes them. */
class SnapshotMaskingTest
{
    private static final String JSON = """
            {"format": 1, "service": "shop", "serviceType": "mysql", "serviceEnabled": %s, "policyVersion": 4,
             "definition": {
               "resources": [
                 {"name": "database", "parent": null, "matcher": "WILDCARD", "caseSensitive": true},
                 {"name": "table", "parent": "database", "matcher": "WILDCARD", "caseSensitive": true},
                 {"name": "column", "parent": "table", "matcher": "WILDCARD", "caseSensitive": false}],
               "accessTypes": [{"name": "select", "impliedGrants": []}, {"name": "update", "impliedGrants": []}],
               "conditions": [],
               "maskTypes": [{"name": "mask_hash", "transformer": "SHA2({col}, 256)"},
                             {"name": "mask_null", "transformer": "NULL"},
                             {"name": "redact", "transformer": null},
                             {"name": "custom", "transformer": "{expr}"}]},
             "policies": [
               {"id": "21", "name": "cards", "type": "DATA_MASK", "priority": "NORMAL", "document": {
                 "resources": {"database": {"values": ["sales"], "excludes": false, "recursive": false}, "table": {"values": ["orders"], "excludes": false, "recursive": false}, "column": {"values": ["card"], "excludes": false, "recursive": false}},
                 "allow": [{"users": ["alice"], "groups": [], "roles": [], "accessTypes": ["select"], "maskType": "mask_hash"},
                           {"users": [], "groups": ["public"], "roles": [], "accessTypes": ["select"], "maskType": "mask_null"}]}},
               {"id": "22", "name": "auditors see cards", "type": "DATA_MASK", "priority": "OVERRIDE", "document": {
                 "resources": {"database": {"values": ["sales"], "excludes": false, "recursive": false}, "table": {"values": ["*"], "excludes": false, "recursive": false}, "column": {"values": ["CARD"], "excludes": false, "recursive": false}},
                 "allow": [{"users": [], "groups": ["auditors"], "roles": [], "accessTypes": ["select"], "maskType": "custom",
                            "maskValue": "CONCAT('****', RIGHT({col}, 4))"}]}},
               {"id": "23", "name": "names", "type": "DATA_MASK", "priority": "NORMAL", "document": {
                 "resources": {"database": {"values": ["sales"], "excludes": false, "recursive": false}, "table": {"values": ["customers"], "excludes": false, "recursive": false}, "column": {"values": ["name"], "excludes": false, "recursive": false}},
                 "allow": [{"users": ["bob"], "groups": [], "roles": [], "accessTypes": ["select"], "maskType": "redact"},
                           {"users": ["carol"], "groups": [], "roles": [], "accessTypes": ["select"], "maskType": "custom"}]}},
               {"id": "31", "name": "own region", "type": "ROW_FILTER", "priority": "NORMAL", "document": {
                 "resources": {"database": {"values": ["sales"], "excludes": false, "recursive": false}, "table": {"values": ["orders"], "excludes": false, "recursive": false}},
                 "allow": [{"users": [], "groups": ["eu"], "roles": [], "accessTypes": ["select"], "rowFilter": "region = 'EU'"}]}},
               {"id": "30", "name": "everyone", "type": "ROW_FILTER", "priority": "NORMAL", "document": {
                 "resources": {"database": {"values": ["sales"], "excludes": false, "recursive": false}, "table": {"values": ["ord*"], "excludes": false, "recursive": false}},
                 "allow": [{"users": [], "groups": ["public"], "roles": [], "accessTypes": ["select"], "rowFilter": "visible = 1"}]}},
               {"id": "40", "name": "later kind", "type": "AUDIT", "priority": "NORMAL", "document": {}}],
             "roles": {},
             "groups": {"auditors": ["dave"], "eu": ["erin"]}}
            """;

    private static Snapshot snapshot(boolean enabled)
    {
        return Snapshot.parse(JSON.formatted(enabled).getBytes(StandardCharsets.UTF_8), Map.of());
    }

    private static AccessRequest column(String user, String table, String column)
    {
        return AccessRequest.builder(user, "select").resource("database", "sales").resource("table", table).resource("column", column).build();
    }

    private static AccessRequest table(String user, String table)
    {
        return AccessRequest.builder(user, "select").resource("database", "sales").resource("table", table).build();
    }

    @Test
    void countsMaskingAndFilteringPoliciesApartFromAccess()
    {
        Snapshot snapshot = snapshot(true);
        assertThat(snapshot.accessPolicies()).isZero();
        assertThat(snapshot.otherPolicies()).isEqualTo(6);
    }

    @Test
    void masksWithTheFirstMatchingItem()
    {
        Snapshot snapshot = snapshot(true);
        DataMask alice = snapshot.mask(column("alice", "orders", "card"));
        assertThat(alice).isNotNull();
        assertThat(alice.policyId()).isEqualTo(21);
        assertThat(alice.policyVersion()).isEqualTo(4);
        assertThat(alice.maskType()).isEqualTo("mask_hash");
        assertThat(alice.expression("`card`")).isEqualTo("SHA2(`card`, 256)");
        assertThat(alice).hasToString("mask_hash by policy 21");
        DataMask anyone = snapshot.mask(column("zoe", "orders", "card"));
        assertThat(anyone).isNotNull();
        assertThat(anyone.expression("`card`")).isEqualTo("NULL");
        assertThat(snapshot.mask(column("alice", "orders", "id"))).isNull();
    }

    @Test
    void overridePoliciesMaskFirstWithTheirValue()
    {
        DataMask auditor = snapshot(true).mask(column("dave", "orders", "card"));
        assertThat(auditor).isNotNull();
        assertThat(auditor.policyId()).as("the snapshot's groups count").isEqualTo(22);
        assertThat(auditor.value()).isEqualTo("CONCAT('****', RIGHT({col}, 4))");
        assertThat(auditor.expression("`card`")).as("the value names the column too").isEqualTo("CONCAT('****', RIGHT(`card`, 4))");
    }

    @Test
    void leavesTypesWithoutTransformerToTheAgentAndRefusesAMissingValue()
    {
        Snapshot snapshot = snapshot(true);
        DataMask bob = snapshot.mask(column("bob", "customers", "name"));
        assertThat(bob).isNotNull();
        assertThat(bob.transformer()).isNull();
        assertThat(bob.expression("name")).isNull();
        DataMask carol = snapshot.mask(column("carol", "customers", "name"));
        assertThat(carol).isNotNull();
        assertThatIllegalStateException().isThrownBy(() -> carol.expression("name")).withMessageContaining("needs a value");
    }

    @Test
    void filtersRowsWithTheSmallestMatchingPolicy()
    {
        Snapshot snapshot = snapshot(true);
        RowFilter everyone = snapshot.rowFilter(table("zoe", "orders"));
        assertThat(everyone).isNotNull();
        assertThat(everyone.policyId()).isEqualTo(30);
        assertThat(everyone.expression()).isEqualTo("visible = 1");
        assertThat(snapshot.rowFilter(table("erin", "orders"))).as("policy 30 also matches and is smaller")
                .satisfies(filter -> assertThat(filter).isNotNull().extracting(RowFilter::policyId).isEqualTo(30L));
        assertThat(snapshot.rowFilter(table("zoe", "customers"))).isNull();
        assertThat(snapshot.rowFilter(table("zoe", "Orders"))).as("tables compare with case").isNull();
    }

    @Test
    void aServiceNotInUseNeitherMasksNorFilters()
    {
        Snapshot snapshot = snapshot(false);
        assertThat(snapshot.mask(column("alice", "orders", "card"))).isNull();
        assertThat(snapshot.rowFilter(table("zoe", "orders"))).isNull();
    }

    @Test
    void refusesMaskTypesTheServiceTypeDoesNotHave()
    {
        String broken = JSON.formatted(true).replace("\"maskType\": \"mask_hash\"", "\"maskType\": \"mask_rot13\"");
        assertThatIllegalArgumentException().isThrownBy(() -> Snapshot.parse(broken.getBytes(StandardCharsets.UTF_8), Map.of()))
                .withMessageContaining("mask_rot13");
        String noFilter = JSON.formatted(true).replace("\"rowFilter\": \"visible = 1\"", "\"rowFilter\": null");
        assertThatIllegalArgumentException().isThrownBy(() -> Snapshot.parse(noFilter.getBytes(StandardCharsets.UTF_8), Map.of()))
                .withMessageContaining("rowFilter");
    }
}

// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.trino.agent;

import io.trino.spi.QueryId;
import io.trino.spi.connector.CatalogSchemaTableName;
import io.trino.spi.connector.EntityKindAndName;
import io.trino.spi.security.AccessDeniedException;
import io.trino.spi.security.Identity;
import io.trino.spi.security.PrincipalType;
import io.trino.spi.security.Privilege;
import io.trino.spi.security.SystemSecurityContext;
import io.trino.spi.security.TrinoPrincipal;
import org.devlive.grantforge.agent.EventFields;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.ThrowingConsumer;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GrantForgeSystemAccessControl479Test
{
    private static final Identity ALICE = Identity.ofUser("alice");
    private static final Identity BOB = Identity.ofUser("bob");
    private static final CatalogSchemaTableName ORDERS = new CatalogSchemaTableName("iceberg", "sales", "orders");
    private static final TrinoPrincipal CAROL = new TrinoPrincipal(PrincipalType.USER, "carol");

    private final TestRuntime test = new TestRuntime(new TrinoSnapshots().allow(List.of("alice"), List.of("all"), "catalog", "iceberg").snapshot());
    private final GrantForgeSystemAccessControl479 control = new GrantForgeSystemAccessControl479(new TrinoAccess(test.runtime(),
            AccessDeniedException::new));

    private static SystemSecurityContext context(Identity identity)
    {
        return new SystemSecurityContext(identity, new QueryId("20261019_000002_00000_abcde"), Instant.now());
    }

    @SuppressWarnings("removal")
    private Map<String, ThrowingConsumer<SystemSecurityContext>> checks()
    {
        Map<String, ThrowingConsumer<SystemSecurityContext>> checks = new LinkedHashMap<>();
        checks.put("show branches", context -> control.checkCanShowBranches(context, ORDERS));
        checks.put("create branch audit", context -> control.checkCanCreateBranch(context, ORDERS, "audit"));
        checks.put("drop branch audit", context -> control.checkCanDropBranch(context, ORDERS, "audit"));
        checks.put("fast-forward branch main", context -> control.checkCanFastForwardBranch(context, ORDERS, "audit", "main"));
        checks.put("refresh view", context -> control.checkCanRefreshView(context, ORDERS));
        checks.put("set materialized view authorization", context -> control.checkCanSetMaterializedViewAuthorization(context, ORDERS, CAROL));
        checks.put("comment on materialized view", context -> control.checkCanSetMaterializedViewComment(context, ORDERS));
        checks.put("set entity authorization", context -> control.checkCanSetEntityAuthorization(context, new EntityKindAndName("MATERIALIZED VIEW",
                List.of("iceberg", "sales", "orders")), CAROL));
        checks.put("select from a branch", context -> control.checkCanSelectFromColumns(context, ORDERS, Optional.of("audit"), Set.of("id")));
        checks.put("insert into a branch", context -> control.checkCanInsertIntoTable(context, ORDERS, Optional.of("audit")));
        checks.put("delete from a branch", context -> control.checkCanDeleteFromTable(context, ORDERS, Optional.of("audit")));
        checks.put("update a branch", context -> control.checkCanUpdateTableColumns(context, ORDERS, Optional.of("audit"), Set.of("id")));
        return checks;
    }

    @Test
    void decidesTheChecksOfTheLaterLines()
    {
        checks().forEach((name, check) -> assertThatCode(() -> check.accept(context(ALICE))).as(name).doesNotThrowAnyException());
        checks().forEach((name, check) -> assertThatThrownBy(() -> check.accept(context(BOB))).as(name).isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("bob may not "));
        assertThat(test.events()).extracting(event -> EventFields.of(event).get("action")).contains("create branch audit",
                "fast-forward branch main", "refresh view", "comment on materialized view");
    }

    @Test
    void refusesPrivilegesOnBranches()
    {
        SystemSecurityContext context = context(ALICE);
        List<ThrowingConsumer<SystemSecurityContext>> statements = List.of(
                c -> control.checkCanGrantTableBranchPrivilege(c, Privilege.INSERT, ORDERS, "audit", CAROL, false),
                c -> control.checkCanDenyTableBranchPrivilege(c, Privilege.INSERT, ORDERS, "audit", CAROL),
                c -> control.checkCanRevokeTableBranchPrivilege(c, Privilege.INSERT, ORDERS, "audit", CAROL, false));
        for (ThrowingConsumer<SystemSecurityContext> statement : statements) {
            assertThatThrownBy(() -> statement.accept(context)).isInstanceOf(AccessDeniedException.class)
                    .hasMessageContaining("on iceberg.sales.orders@audit: " + TrinoAccess.MANAGED);
        }
    }
}

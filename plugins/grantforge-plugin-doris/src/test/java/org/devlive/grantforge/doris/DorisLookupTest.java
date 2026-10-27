// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.doris;

import org.devlive.grantforge.plugin.api.LookupException;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.sql.SQLInvalidAuthorizationSpecException;
import java.sql.SQLNonTransientConnectionException;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class DorisLookupTest
{
    @Test
    void readsTheLevelsAboveFromThePolicy()
    {
        DorisLookup lookup = DorisLookup.of(DorisProvider.COLUMN, " re ", Map.of(DorisProvider.CATALOG, List.of(" internal "),
                DorisProvider.DATABASE, List.of("sales", " "), DorisProvider.TABLE, List.of("ord*")), 0);
        assertThat(lookup.typed()).isEqualTo("re");
        assertThat(lookup.catalogs()).containsExactly("internal");
        assertThat(lookup.databases()).as("blank values name nothing").containsExactly("sales");
        assertThat(lookup.tables()).containsExactly("ord*");
        assertThat(lookup.limit()).as("at least one value").isEqualTo(1);
        assertThat(DorisLookup.of(DorisProvider.WORKLOAD_GROUP, "", Map.of(), 5).catalogs()).isEmpty();
    }

    @Test
    void refusesALevelTheServiceTypeDoesNotHave()
    {
        assertThatIllegalArgumentException().isThrownBy(() -> DorisLookup.of("partition", "", Map.of(), 10)).withMessageContaining("partition");
    }

    @Test
    void matchesNamesAgainstThePolicysWildcards()
    {
        List<String> names = List.of("orders", "Orders_2026", "refunds", "o.x");
        assertThat(DorisLookup.matching(names, List.of(), true)).as("no wildcard is every name").isEqualTo(names);
        assertThat(DorisLookup.matching(names, List.of("ord*"), true)).containsExactly("orders");
        assertThat(DorisLookup.matching(names, List.of("ord*"), false)).containsExactly("orders", "Orders_2026");
        assertThat(DorisLookup.matching(names, List.of("o?x"), true)).as("a dot is no wildcard").containsExactly("o.x");
        assertThat(DorisLookup.matching(names, List.of("*", "refunds"), true)).isEqualTo(names);
        assertThat(DorisLookup.matching(names, List.of("re*", "o.x"), true)).containsExactly("refunds", "o.x");
    }

    @Test
    void looksInAtMostAHundredNames()
    {
        List<String> names = java.util.stream.IntStream.range(0, 150).mapToObj(index -> "t" + index).toList();
        assertThat(DorisLookup.matching(names, List.of("t*"), true)).hasSize(DorisLookup.MAX_PARENTS);
    }

    @Test
    void quotesNamesWithBackquotes()
    {
        assertThat(DorisLookup.quote("sales")).isEqualTo("`sales`");
        assertThat(DorisLookup.quote("odd`name")).isEqualTo("`odd``name`");
    }

    @Test
    void readsTheReleaseFromTheVersionComment()
    {
        assertThat(DorisLookup.release("doris version doris-4.1.5-release-917b20be838")).isEqualTo("4.1.5");
        assertThat(DorisLookup.release("doris version doris-2.1.11-rc01-2b5e3a4")).isEqualTo("2.1.11");
        assertThat(DorisLookup.release("something else")).isEqualTo("something else");
        assertThat(DorisLookup.release(null)).isEqualTo("of an unknown release");
    }

    @Test
    void saysWhyTheFrontendFailed()
    {
        assertThat(DorisLookup.failure(new SQLException("(conn=12) Access denied for user 'lookup'@'10.0.0.1'\nmore", "28000", 1045)))
                .satisfies(failure -> {
                    assertThat(failure.getReason()).isEqualTo(LookupException.Reason.AUTHENTICATION_FAILED);
                    assertThat(failure.getMessage()).isEqualTo("Doris refused the sign-in: Access denied for user 'lookup'@'10.0.0.1'");
                });
        assertThat(DorisLookup.failure(new SQLInvalidAuthorizationSpecException("refused")).getReason())
                .isEqualTo(LookupException.Reason.AUTHENTICATION_FAILED);
        assertThat(DorisLookup.failure(new SQLException("denied", "42000", 1142)).getReason()).isEqualTo(LookupException.Reason.ACCESS_DENIED);
        assertThat(DorisLookup.failure(new SQLNonTransientConnectionException("gone")).getReason()).isEqualTo(LookupException.Reason.UNREACHABLE);
        assertThat(DorisLookup.failure(new SQLException("timeout", "08000")).getReason()).isEqualTo(LookupException.Reason.UNREACHABLE);
        LookupException other = DorisLookup.failure(new SQLException((String) null));
        assertThat(other.getReason()).isEqualTo(LookupException.Reason.FAILED);
        assertThat(other.getMessage()).isEqualTo("Doris failed the lookup: SQLException");
    }
}

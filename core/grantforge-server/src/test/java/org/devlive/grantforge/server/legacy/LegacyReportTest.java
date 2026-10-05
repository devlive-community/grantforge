// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.legacy;

import org.devlive.grantforge.server.legacy.LegacyReport.Kind;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LegacyReportTest
{
    private static LegacyReport report(boolean applied)
    {
        LegacyReport report = new LegacyReport(applied, "jdbc:mysql://db/authx", "acme", "legacy");
        report.created(Kind.USERS);
        report.created(Kind.USERS);
        report.existing(Kind.ROLES);
        report.mapped(Kind.USERS, 2, 812_345_678_901_234_567L);
        report.skipped(Kind.USERS, 1L, "系统用户", "not a valid login name");
        report.noted(Kind.ROLES, 1, "管理员", "the code GLY became gly");
        report.wildcard(9, "/api/v1/user/*", "/api/v1/user/**");
        return report;
    }

    @Test
    void countsWhatWasDone()
    {
        LegacyReport report = report(true);

        assertThat(report.createdCount(Kind.USERS)).isEqualTo(2);
        assertThat(report.createdCount()).isEqualTo(2);
        assertThat(report.existingCount(Kind.ROLES)).isEqualTo(1);
        assertThat(report.skippedCount()).isEqualTo(1);
        assertThat(report.issues()).hasSize(2);
        assertThat(report.newId(Kind.USERS, 2)).isEqualTo(812_345_678_901_234_567L);
        assertThat(report.newId(Kind.USERS, 3)).isNull();
        assertThat(report.newId(Kind.MENUS, 2)).isNull();
    }

    @Test
    void summarizesInOneLinePerKind()
    {
        assertThat(report(true).summary()).startsWith("Imported").contains("USERS: 2 created, 0 existing")
                .endsWith("1 left out, 1 wildcard URLs to check");
        assertThat(report(false).summary()).startsWith("Dry run, nothing written");
    }

    @Test
    void writesJsonWithIdsAsStrings()
    {
        String json = report(false).json();

        assertThat(json).contains("\"mode\" : \"dry-run\"", "\"tenant\" : \"acme\"", "\"users\" : {", "\"2\" : \"812345678901234567\"",
                "\"pattern\" : \"/api/v1/user/**\"", "\"outcome\" : \"skipped\"", "\"label\" : \"系统用户\"");
        assertThat(json.indexOf("\"created\" : 2")).isLessThan(json.indexOf("\"existing\" : 0"));
    }
}

// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.identity.application;

import org.devlive.grantforge.common.error.GrantForgeException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ImportSheetTest
{
    @Test
    void readsValuesByColumnNameInAnyCaseAndOrder()
    {
        ImportSheet sheet = new ImportSheet(List.of(List.of(" Name ", "CODE", "extra"), List.of(" 总部 ", "hq"),
                List.of("", "lab", "x")), Set.of("code"), 10);

        assertThat(sheet.size()).isEqualTo(2);
        assertThat(sheet.value(0, "name")).isEqualTo("总部");
        assertThat(sheet.value(0, "extra")).isNull();
        assertThat(sheet.value(1, "name")).isNull();
        assertThat(sheet.value(1, "missing")).isNull();
        assertThat(ImportSheet.recordOf(0)).isEqualTo(2);
    }

    @Test
    void rejectsEmptyFilesMissingColumnsAndTooManyRows()
    {
        assertThatThrownBy(() -> new ImportSheet(List.of(List.of("code")), Set.of("code"), 10))
                .isInstanceOfSatisfying(GrantForgeException.class,
                        error -> assertThat(error.getErrorCode()).isEqualTo(IdentityErrorCode.IMPORT_EMPTY));
        assertThatThrownBy(() -> new ImportSheet(List.of(List.of("name"), List.of("x")), Set.of("code", "name"), 10))
                .isInstanceOfSatisfying(GrantForgeException.class, error -> {
                    assertThat(error.getErrorCode()).isEqualTo(IdentityErrorCode.IMPORT_MISSING_COLUMN);
                    assertThat(error.getArguments()).containsExactly("code");
                });
        assertThatThrownBy(() -> new ImportSheet(List.of(List.of("code"), List.of("a"), List.of("b")), Set.of("code"), 1))
                .isInstanceOfSatisfying(GrantForgeException.class, error -> {
                    assertThat(error.getErrorCode()).isEqualTo(IdentityErrorCode.IMPORT_TOO_MANY_ROWS);
                    assertThat(error.getArguments()).containsExactly(1);
                });
    }
}

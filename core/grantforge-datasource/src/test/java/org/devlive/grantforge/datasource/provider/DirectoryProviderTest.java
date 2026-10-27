// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.datasource.provider;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DirectoryProviderTest
{
    @Test
    void snapshotsAndMembershipsCannotBeChangedAfterConstruction()
    {
        var memberships = new ArrayList<>(List.of("1"));
        var employee = new DirectoryProvider.Employee("user", "user@example.com", memberships);
        var employees = new ArrayList<>(List.of(employee));
        var departments = new ArrayList<>(List.of(new DirectoryProvider.Department("1", null, "Root", 0)));
        var snapshot = new DirectoryProvider.Snapshot(departments, employees, "scope");
        memberships.clear();
        departments.clear();
        employees.clear();
        assertThat(snapshot.departments()).hasSize(1);
        assertThat(snapshot.employees()).containsExactly(employee);
        assertThat(employee.departments()).containsExactly("1");
        assertThatThrownBy(() -> employee.departments().clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> snapshot.departments().clear()).isInstanceOf(UnsupportedOperationException.class);
    }
}

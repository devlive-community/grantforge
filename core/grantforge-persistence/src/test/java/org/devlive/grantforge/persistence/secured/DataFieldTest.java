// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.persistence.secured;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DataFieldTest
{
    @Test
    void keepsItsOwnCopyOfTheChoices()
    {
        List<String> choices = new ArrayList<>(List.of("A"));
        DataField field = new DataField("status", "Status", DataFieldType.CHOICE, choices);
        choices.add("B");
        assertThat(field.choices()).containsExactly("A");
    }
}

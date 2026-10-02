// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.persistence.secured;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FilterableFieldTest
{
    @FilterableField("Status")
    private String status = "";

    @Test
    void marksFieldsWithTheirName() throws NoSuchFieldException
    {
        assertThat(FilterableFieldTest.class.getDeclaredField("status").getAnnotation(FilterableField.class).value()).isEqualTo("Status");
        assertThat(status).isEmpty();
    }
}

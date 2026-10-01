// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.identity.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PositionTest
{
    @Test
    void normalizesAndValidatesItsDetails()
    {
        Position position = Position.create(" CFO ", " 财务总监 ", " 管财务 ", 3);

        assertThat(position).extracting(Position::getCode, Position::getName, Position::getDescription, Position::getSortOrder)
                .containsExactly("cfo", "财务总监", "管财务", 3);
        position.change("cfo", "CFO", "", 0);
        assertThat(position.getDescription()).isNull();
        assertThatThrownBy(() -> position.change("c f o", "CFO", null, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> position.change("cfo", " ", null, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> position.change("cfo", "x".repeat(Position.NAME_MAX + 1), null, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> position.change("cfo", "CFO", "x".repeat(Position.DESCRIPTION_MAX + 1), 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> position.change("cfo", "CFO", null, -1)).isInstanceOf(IllegalArgumentException.class);
        assertThat(position.getName()).isEqualTo("CFO");
    }
}

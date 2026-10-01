// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.common.csv;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CsvTest
{
    @Test
    void parsesQuotedFieldsLineBreaksAndBothLineEnds()
    {
        String text = Csv.BYTE_ORDER_MARK + "username,displayName\r\nalice,\"Liddell, Alice\"\n\nbob,\"say \"\"hi\"\"\"\ncarol,\"two\nlines\"\r\n,";

        assertThat(Csv.parse(text)).containsExactly(
                List.of("username", "displayName"),
                List.of("alice", "Liddell, Alice"),
                List.of("bob", "say \"hi\""),
                List.of("carol", "two\nlines"),
                List.of("", ""));
        assertThat(Csv.parse("")).isEmpty();
        assertThat(Csv.parse("a,b\r")).containsExactly(List.of("a", "b"));
        assertThat(Csv.parse("中文,名称")).containsExactly(List.of("中文", "名称"));
    }

    @Test
    void rejectsBrokenQuoting()
    {
        assertThatThrownBy(() -> Csv.parse("a,\"open\nb,c")).isInstanceOfSatisfying(CsvException.class,
                error -> assertThat(error.getLine()).isEqualTo(2));
        assertThatThrownBy(() -> Csv.parse("a,\"b\"c")).isInstanceOfSatisfying(CsvException.class,
                error -> assertThat(error.getLine()).isOne());
        assertThatThrownBy(() -> Csv.parse("x\n\"q\"x")).hasMessageContaining("line 2");
    }

    @Test
    void writesWhatItReadsAndDefusesFormulas()
    {
        List<List<String>> records = List.of(List.of("name", "note"), List.of("Liddell, Alice", "say \"hi\""),
                List.of("two\nlines", ""));

        String written = Csv.write(records);
        assertThat(written).startsWith(Csv.BYTE_ORDER_MARK).endsWith("\r\n");
        assertThat(Csv.parse(written)).isEqualTo(records);
        assertThat(Csv.write(List.of(List.of("=1+2", "+x", "-y", "@z", "\tt", "plain"))))
                .isEqualTo(Csv.BYTE_ORDER_MARK + "'=1+2,'+x,'-y,'@z,'\tt,plain\r\n");
        assertThat(Csv.write(List.of(List.of("\rcr")))).isEqualTo(Csv.BYTE_ORDER_MARK + "\"'\rcr\"\r\n");
    }
}

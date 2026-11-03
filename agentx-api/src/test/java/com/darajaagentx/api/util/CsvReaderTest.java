package com.darajaagentx.api.util;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CsvReaderTest {

    @Test
    void readsPlainRows() {
        assertThat(CsvReader.parse("a,b\n1,2\n")).containsExactly(List.of("a", "b"), List.of("1", "2"));
    }

    @Test
    void readsQuotedValuesWithCommasQuotesAndLineBreaks() {
        List<List<String>> rows = CsvReader.parse("a,b\n\"x,y\",\"he said \"\"hi\"\"\"\n\"two\nlines\",z\n");
        assertThat(rows.get(1)).containsExactly("x,y", "he said \"hi\"");
        assertThat(rows.get(2)).containsExactly("two\nlines", "z");
    }

    @Test
    void ignoresTheByteOrderMarkAndAnyLineEnding() {
        assertThat(CsvReader.parse("﻿a,b\r\n1,2\r3,4").get(0)).containsExactly("a", "b");
        assertThat(CsvReader.parse("﻿a,b\r\n1,2\r3,4")).hasSize(3);
    }

    @Test
    void understandsSemicolonsAndTabs() {
        assertThat(CsvReader.parse("a;b\n1;2\n").get(1)).containsExactly("1", "2");
        assertThat(CsvReader.parse("a\tb\n1\t2\n").get(1)).containsExactly("1", "2");
    }

    @Test
    void skipsEmptyLinesAndLinesOfOnlyCommas() {
        assertThat(CsvReader.parse("a,b\n\n,\n1,2")).hasSize(2);
    }

    @Test
    void emptyTextGivesNoRows() {
        assertThat(CsvReader.parse("")).isEmpty();
        assertThat(CsvReader.parse(null)).isEmpty();
    }
}

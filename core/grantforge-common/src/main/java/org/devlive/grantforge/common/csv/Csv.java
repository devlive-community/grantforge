// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.common.csv;

import java.util.ArrayList;
import java.util.List;

import static java.util.Objects.requireNonNull;

/**
 * Comma-separated values as RFC 4180 describes them, in the shape spreadsheets exchange: fields may be quoted,
 * quoted fields may contain commas, quotes (doubled) and line breaks, and lines end with CRLF or LF. A leading
 * byte order mark is ignored on reading and written on writing, so Excel recognises UTF-8.
 */
public final class Csv
{
    /** The byte order mark that tells spreadsheets the file is UTF-8. */
    public static final String BYTE_ORDER_MARK = "﻿";

    private Csv()
    {
    }

    /**
     * Parses a whole file; blank lines are skipped.
     *
     * @param text the file content
     * @return the records, each a list of fields
     * @throws CsvException if a quoted field is not closed or text follows a closing quote
     */
    public static List<List<String>> parse(String text)
    {
        String input = requireNonNull(text, "text");
        if (input.startsWith(BYTE_ORDER_MARK)) {
            input = input.substring(1);
        }
        List<List<String>> records = new ArrayList<>();
        // Reused for every record: addRecord copies it.
        List<String> fields = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        int line = 1;
        int i = 0;
        boolean quoted = false;
        boolean afterQuote = false;
        while (i < input.length()) {
            char c = input.charAt(i);
            if (quoted) {
                if (c == '"') {
                    if (i + 1 < input.length() && input.charAt(i + 1) == '"') {
                        field.append('"');
                        i++;
                    }
                    else {
                        quoted = false;
                        afterQuote = true;
                    }
                }
                else {
                    if (c == '\n') {
                        line++;
                    }
                    field.append(c);
                }
            }
            else if (c == ',') {
                fields.add(field.toString());
                field.setLength(0);
                afterQuote = false;
            }
            else if (c == '\n' || c == '\r') {
                if (c == '\r' && i + 1 < input.length() && input.charAt(i + 1) == '\n') {
                    i++;
                }
                fields.add(field.toString());
                addRecord(records, fields);
                fields.clear();
                field.setLength(0);
                afterQuote = false;
                line++;
            }
            else if (afterQuote) {
                throw new CsvException(line, "text after a closing quote");
            }
            else if (c == '"' && field.isEmpty()) {
                quoted = true;
            }
            else {
                field.append(c);
            }
            i++;
        }
        if (quoted) {
            throw new CsvException(line, "a quoted field is not closed");
        }
        fields.add(field.toString());
        addRecord(records, fields);
        return records;
    }

    private static void addRecord(List<List<String>> records, List<String> fields)
    {
        // A line without any content is a blank line, not a record with one empty field.
        if (!(fields.size() == 1 && fields.get(0).isEmpty())) {
            records.add(List.copyOf(fields));
        }
    }

    /**
     * Writes records with CRLF line ends, preceded by the byte order mark. Fields are quoted when they contain a
     * comma, quote or line break; fields a spreadsheet would run as a formula (starting with {@code =}, {@code +},
     * {@code -}, {@code @}, tab or carriage return) get a leading apostrophe, so an export cannot inject formulas.
     *
     * @param records the records
     * @return the file content
     */
    public static String write(List<List<String>> records)
    {
        StringBuilder out = new StringBuilder(BYTE_ORDER_MARK);
        for (List<String> fields : requireNonNull(records, "records")) {
            for (int i = 0; i < fields.size(); i++) {
                if (i > 0) {
                    out.append(',');
                }
                out.append(escape(fields.get(i)));
            }
            out.append("\r\n");
        }
        return out.toString();
    }

    private static String escape(String value)
    {
        String text = value;
        if (!text.isEmpty() && "=+-@\t\r".indexOf(text.charAt(0)) >= 0) {
            text = "'" + text;
        }
        if (text.indexOf(',') >= 0 || text.indexOf('"') >= 0 || text.indexOf('\n') >= 0 || text.indexOf('\r') >= 0) {
            return '"' + text.replace("\"", "\"\"") + '"';
        }
        return text;
    }
}

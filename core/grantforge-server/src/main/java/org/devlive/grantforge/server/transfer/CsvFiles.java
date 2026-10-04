// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.transfer;

import org.devlive.grantforge.common.csv.Csv;
import org.devlive.grantforge.common.csv.CsvException;
import org.devlive.grantforge.common.error.GrantForgeException;
import org.devlive.grantforge.identity.application.IdentityErrorCode;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

/** Reading uploaded CSV files and answering with downloadable ones. */
public final class CsvFiles
{
    /** What spreadsheets in Chinese locales save "CSV" as, unless asked for UTF-8. */
    private static final Charset LEGACY = Charset.forName("GB18030");

    private CsvFiles()
    {
    }

    /**
     * Parses an uploaded file: UTF-8 when it is valid UTF-8, otherwise GB18030 (a superset of GBK).
     *
     * @param content the file's bytes
     * @return the records
     * @throws GrantForgeException {@link IdentityErrorCode#IMPORT_EMPTY} for an empty file or
     *         {@link IdentityErrorCode#IMPORT_MALFORMED} for broken CSV
     */
    static List<List<String>> read(byte[] content)
    {
        if (content.length == 0) {
            throw new GrantForgeException(IdentityErrorCode.IMPORT_EMPTY, "empty file");
        }
        try {
            return Csv.parse(decode(content));
        }
        catch (CsvException broken) {
            throw new GrantForgeException(IdentityErrorCode.IMPORT_MALFORMED, String.valueOf(broken.getMessage()), broken,
                    broken.getLine());
        }
    }

    private static String decode(byte[] content)
    {
        try {
            return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(content)).toString();
        }
        catch (CharacterCodingException notUtf8) {
            return new String(content, LEGACY);
        }
    }

    /**
     * Answers with a CSV download named after its content and today's date.
     *
     * @param name the file name's start, such as {@code users}
     * @param records the records
     * @param clock source of today's date
     * @return the response
     */
    public static ResponseEntity<byte[]> download(String name, List<List<String>> records, Clock clock)
    {
        String fileName = name + "-" + LocalDate.now(clock) + ".csv";
        return ResponseEntity.ok()
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(fileName).build().toString())
                .body(Csv.write(records).getBytes(StandardCharsets.UTF_8));
    }
}

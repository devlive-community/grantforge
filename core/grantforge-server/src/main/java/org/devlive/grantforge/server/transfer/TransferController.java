// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.transfer;

import org.devlive.grantforge.identity.application.OrgTransferService;
import org.devlive.grantforge.identity.application.UserFilter;
import org.devlive.grantforge.identity.application.UserTransferService;
import org.devlive.grantforge.identity.domain.UserState;
import org.devlive.grantforge.server.error.ErrorMessages;
import org.devlive.grantforge.server.security.SessionUser;
import org.devlive.grantforge.server.web.PathIds;
import org.jspecify.annotations.Nullable;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Clock;

import static java.util.Objects.requireNonNull;

/**
 * CSV export and import of accounts and departments. An import is checked first ({@code apply=false}) and applied
 * in a second call; it writes all rows or none.
 */
@RestController
public final class TransferController
{
    private final UserTransferService users;
    private final OrgTransferService units;
    private final ErrorMessages messages;
    private final Clock clock;

    /**
     * Creates the controller.
     *
     * @param users account export and import
     * @param units department export and import
     * @param messages localises the problems of an import
     * @param clock names files after today's date
     */
    public TransferController(UserTransferService users, OrgTransferService units, ErrorMessages messages, Clock clock)
    {
        this.users = requireNonNull(users, "users");
        this.units = requireNonNull(units, "units");
        this.messages = requireNonNull(messages, "messages");
        this.clock = requireNonNull(clock, "clock");
    }

    /**
     * Downloads the accounts a filter matches as CSV.
     *
     * @param user the session's principal
     * @param q text the login name, display name or e-mail address contains
     * @param state whether the accounts can sign in right now
     * @param unitId a department whose members (and members of its sub-departments) to export
     * @return the CSV file
     */
    @GetMapping(value = "/api/v1/users/export", produces = "text/csv")
    public ResponseEntity<byte[]> exportUsers(@AuthenticationPrincipal SessionUser user,
            @RequestParam(required = false) @Nullable String q, @RequestParam(required = false) @Nullable UserState state,
            @RequestParam(required = false) @Nullable String unitId)
    {
        Long unit = unitId == null || unitId.isBlank() ? null : PathIds.parse(unitId, "department");
        return CsvFiles.download("users", users.export(user.accountId(), new UserFilter(q, state, unit, true)), clock);
    }

    /**
     * Checks or applies a CSV file of new accounts.
     *
     * @param user the session's principal
     * @param file the CSV file
     * @param apply whether to create the accounts; only checks if omitted
     * @return the report
     * @throws IOException if the upload cannot be read
     */
    @PostMapping(value = "/api/v1/users/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ImportReportResponse importUsers(@AuthenticationPrincipal SessionUser user, @RequestPart("file") MultipartFile file,
            @RequestParam(defaultValue = "false") boolean apply) throws IOException
    {
        return ImportReportResponse.from(users.importUsers(user.accountId(), CsvFiles.read(file.getBytes()), apply), messages);
    }

    /**
     * Downloads the organization tree as CSV.
     *
     * @param user the session's principal
     * @return the CSV file
     */
    @GetMapping(value = "/api/v1/org-units/export", produces = "text/csv")
    public ResponseEntity<byte[]> exportUnits(@AuthenticationPrincipal SessionUser user)
    {
        return CsvFiles.download("departments", units.export(user.accountId()), clock);
    }

    /**
     * Checks or applies a CSV file of new departments.
     *
     * @param user the session's principal
     * @param file the CSV file
     * @param apply whether to create the departments; only checks if omitted
     * @return the report
     * @throws IOException if the upload cannot be read
     */
    @PostMapping(value = "/api/v1/org-units/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ImportReportResponse importUnits(@AuthenticationPrincipal SessionUser user, @RequestPart("file") MultipartFile file,
            @RequestParam(defaultValue = "false") boolean apply) throws IOException
    {
        return ImportReportResponse.from(units.importUnits(user.accountId(), CsvFiles.read(file.getBytes()), apply), messages);
    }
}

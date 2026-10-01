// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.position;

import jakarta.validation.Valid;
import org.devlive.grantforge.common.page.PageQuery;
import org.devlive.grantforge.common.page.PageResult;
import org.devlive.grantforge.identity.application.PositionService;
import org.devlive.grantforge.identity.domain.MemberRow;
import org.devlive.grantforge.identity.domain.PositionRow;
import org.devlive.grantforge.server.group.MemberResponse;
import org.devlive.grantforge.server.security.SessionUser;
import org.devlive.grantforge.server.web.PathIds;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static java.util.Objects.requireNonNull;

/** Positions of the administrator's tenant; accounts get positions through the user administration. */
@RestController
@RequestMapping("/api/v1/positions")
public final class PositionController
{
    private final PositionService positions;

    /**
     * Creates the controller.
     *
     * @param positions the position administration
     */
    public PositionController(PositionService positions)
    {
        this.positions = requireNonNull(positions, "positions");
    }

    /**
     * Lists positions whose code or name contains a text, in list order.
     *
     * @param user the session's principal
     * @param q the text to look for; every position if omitted
     * @param page 1-based page number, 1 if omitted
     * @param size page size, 20 if omitted
     * @return the positions
     */
    @GetMapping
    public PageResult<PositionResponse> list(@AuthenticationPrincipal SessionUser user,
            @RequestParam(required = false) @Nullable String q, @RequestParam(required = false) @Nullable Integer page,
            @RequestParam(required = false) @Nullable Integer size)
    {
        PageResult<PositionRow> found = positions.list(user.accountId(), q, PageQuery.of(page, size));
        return new PageResult<>(found.items().stream().map(PositionResponse::from).toList(), found.page(), found.size(),
                found.total());
    }

    /**
     * Returns every position in list order, for choosing an account's positions.
     *
     * @param user the session's principal
     * @return the positions
     */
    @GetMapping("/options")
    public List<PositionOptionResponse> options(@AuthenticationPrincipal SessionUser user)
    {
        return positions.options(user.accountId()).stream().map(PositionOptionResponse::from).toList();
    }

    /**
     * Creates a position.
     *
     * @param user the session's principal
     * @param body the position
     * @return the position
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PositionResponse create(@AuthenticationPrincipal SessionUser user, @Valid @RequestBody PositionRequest body)
    {
        return PositionResponse.from(positions.create(user.accountId(), body.code(), body.name(), body.description(),
                body.order()));
    }

    /**
     * Changes a position's details.
     *
     * @param user the session's principal
     * @param id the position
     * @param body the new details
     * @return the position
     */
    @PutMapping("/{id}")
    public PositionResponse update(@AuthenticationPrincipal SessionUser user, @PathVariable String id,
            @Valid @RequestBody PositionRequest body)
    {
        return PositionResponse.from(positions.update(user.accountId(), position(id), body.code(), body.name(),
                body.description(), body.order()));
    }

    /**
     * Deletes a position; every account holding it loses it.
     *
     * @param user the session's principal
     * @param id the position
     */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal SessionUser user, @PathVariable String id)
    {
        positions.delete(user.accountId(), position(id));
    }

    /**
     * Lists the accounts holding a position, by login name.
     *
     * @param user the session's principal
     * @param id the position
     * @param page 1-based page number, 1 if omitted
     * @param size page size, 20 if omitted
     * @return the holders
     */
    @GetMapping("/{id}/holders")
    public PageResult<MemberResponse> holders(@AuthenticationPrincipal SessionUser user, @PathVariable String id,
            @RequestParam(required = false) @Nullable Integer page, @RequestParam(required = false) @Nullable Integer size)
    {
        PageResult<MemberRow> found = positions.holders(user.accountId(), position(id), PageQuery.of(page, size));
        return new PageResult<>(found.items().stream().map(MemberResponse::from).toList(), found.page(), found.size(),
                found.total());
    }

    private static long position(String id)
    {
        return PathIds.parse(id, "position");
    }
}

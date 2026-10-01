// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.group;

import jakarta.validation.Valid;
import org.devlive.grantforge.common.page.PageQuery;
import org.devlive.grantforge.common.page.PageResult;
import org.devlive.grantforge.identity.application.GroupService;
import org.devlive.grantforge.identity.domain.GroupMemberRow;
import org.devlive.grantforge.identity.domain.GroupRow;
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

import static java.util.Objects.requireNonNull;

/** User groups of the administrator's tenant and their members. */
@RestController
@RequestMapping("/api/v1/groups")
public final class GroupController
{
    private final GroupService groups;

    /**
     * Creates the controller.
     *
     * @param groups the group administration
     */
    public GroupController(GroupService groups)
    {
        this.groups = requireNonNull(groups, "groups");
    }

    /**
     * Lists groups whose code or name contains a text, by name.
     *
     * @param user the session's principal
     * @param q the text to look for; every group if omitted
     * @param page 1-based page number, 1 if omitted
     * @param size page size, 20 if omitted
     * @return the groups
     */
    @GetMapping
    public PageResult<GroupResponse> list(@AuthenticationPrincipal SessionUser user,
            @RequestParam(required = false) @Nullable String q, @RequestParam(required = false) @Nullable Integer page,
            @RequestParam(required = false) @Nullable Integer size)
    {
        PageResult<GroupRow> found = groups.list(user.accountId(), q, PageQuery.of(page, size));
        return new PageResult<>(found.items().stream().map(GroupResponse::from).toList(), found.page(), found.size(),
                found.total());
    }

    /**
     * Creates a group.
     *
     * @param user the session's principal
     * @param body the group
     * @return the group
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public GroupResponse create(@AuthenticationPrincipal SessionUser user, @Valid @RequestBody GroupRequest body)
    {
        return GroupResponse.from(groups.create(user.accountId(), body.code(), body.name(), body.description()));
    }

    /**
     * Changes a group's details.
     *
     * @param user the session's principal
     * @param id the group
     * @param body the new details
     * @return the group
     */
    @PutMapping("/{id}")
    public GroupResponse update(@AuthenticationPrincipal SessionUser user, @PathVariable String id,
            @Valid @RequestBody GroupRequest body)
    {
        return GroupResponse.from(groups.update(user.accountId(), group(id), body.code(), body.name(), body.description()));
    }

    /**
     * Deletes a group; its members keep their accounts.
     *
     * @param user the session's principal
     * @param id the group
     */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal SessionUser user, @PathVariable String id)
    {
        groups.delete(user.accountId(), group(id));
    }

    /**
     * Lists a group's members whose login name, display name or e-mail address contains a text.
     *
     * @param user the session's principal
     * @param id the group
     * @param q the text to look for; every member if omitted
     * @param page 1-based page number, 1 if omitted
     * @param size page size, 20 if omitted
     * @return the members, by login name
     */
    @GetMapping("/{id}/members")
    public PageResult<GroupMemberResponse> members(@AuthenticationPrincipal SessionUser user, @PathVariable String id,
            @RequestParam(required = false) @Nullable String q, @RequestParam(required = false) @Nullable Integer page,
            @RequestParam(required = false) @Nullable Integer size)
    {
        PageResult<GroupMemberRow> found = groups.members(user.accountId(), group(id), q, PageQuery.of(page, size));
        return new PageResult<>(found.items().stream().map(GroupMemberResponse::from).toList(), found.page(),
                found.size(), found.total());
    }

    /**
     * Adds accounts to a group; accounts that already belong are skipped.
     *
     * @param user the session's principal
     * @param id the group
     * @param body the accounts
     * @return how many accounts joined
     */
    @PostMapping("/{id}/members")
    public MemberChangeResponse addMembers(@AuthenticationPrincipal SessionUser user, @PathVariable String id,
            @Valid @RequestBody MemberBatchRequest body)
    {
        return new MemberChangeResponse(groups.addMembers(user.accountId(), group(id), body.ids()));
    }

    /**
     * Removes accounts from a group; accounts that do not belong are skipped.
     *
     * @param user the session's principal
     * @param id the group
     * @param body the accounts
     * @return how many accounts left
     */
    @PostMapping("/{id}/members/remove")
    public MemberChangeResponse removeMembers(@AuthenticationPrincipal SessionUser user, @PathVariable String id,
            @Valid @RequestBody MemberBatchRequest body)
    {
        return new MemberChangeResponse(groups.removeMembers(user.accountId(), group(id), body.ids()));
    }

    private static long group(String id)
    {
        return PathIds.parse(id, "group");
    }
}

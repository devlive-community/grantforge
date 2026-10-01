// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.devlive.grantforge.common.error.CommonErrorCode;
import org.devlive.grantforge.common.error.GrantForgeException;
import org.devlive.grantforge.common.page.PageQuery;
import org.devlive.grantforge.common.page.PageResult;
import org.devlive.grantforge.identity.application.ActiveSession;
import org.devlive.grantforge.identity.application.ConsoleSessionService;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static java.util.Objects.requireNonNull;

/**
 * Signed-in console sessions: everyone sees and ends their own; administrators see and end the organization's.
 * Ending a session signs its browser out at the next request.
 */
@RestController
public final class SessionController
{
    private final ConsoleSessionService sessions;

    /**
     * Creates the controller.
     *
     * @param sessions the session index
     */
    public SessionController(ConsoleSessionService sessions)
    {
        this.sessions = requireNonNull(sessions, "sessions");
    }

    /**
     * Lists the signed-in user's sessions, most recently used first.
     *
     * @param user the session's principal
     * @param request the request, whose session is marked as current
     * @return the sessions
     */
    @GetMapping("/api/v1/me/sessions")
    public List<SessionResponse> own(@AuthenticationPrincipal SessionUser user, HttpServletRequest request)
    {
        return sessions.listOwn(user.accountId(), currentId(request)).stream().map(SessionResponse::from).toList();
    }

    /**
     * Ends one of the signed-in user's sessions; ending the current one signs out.
     *
     * @param user the session's principal
     * @param id the session's handle
     * @param request the request
     */
    @DeleteMapping("/api/v1/me/sessions/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revokeOwn(@AuthenticationPrincipal SessionUser user, @PathVariable String id, HttpServletRequest request)
    {
        invalidateIf(sessions.revokeOwn(user.accountId(), handle(id), currentId(request)), request);
    }

    /**
     * Lists the organization's sessions, most recently used first.
     *
     * @param user the session's principal
     * @param page 1-based page number, 1 if omitted
     * @param size page size, 20 if omitted
     * @param request the request, whose session is marked as current
     * @return the sessions
     */
    @GetMapping("/api/v1/sessions")
    public PageResult<SessionResponse> all(@AuthenticationPrincipal SessionUser user,
            @RequestParam(required = false) @Nullable Integer page, @RequestParam(required = false) @Nullable Integer size,
            HttpServletRequest request)
    {
        PageQuery query = PageQuery.of(page, size);
        PageResult<ActiveSession> found = sessions.listAll(user.accountId(), query, currentId(request));
        return new PageResult<>(found.items().stream().map(SessionResponse::from).toList(), found.page(), found.size(),
                found.total());
    }

    /**
     * Ends any session of the organization.
     *
     * @param user the session's principal
     * @param id the session's handle
     * @param request the request
     */
    @DeleteMapping("/api/v1/sessions/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revoke(@AuthenticationPrincipal SessionUser user, @PathVariable String id, HttpServletRequest request)
    {
        invalidateIf(sessions.revoke(user.accountId(), handle(id), currentId(request)), request);
    }

    private static @Nullable String currentId(HttpServletRequest request)
    {
        HttpSession session = request.getSession(false);
        return session == null ? null : session.getId();
    }

    private static long handle(String id)
    {
        try {
            return Long.parseLong(id);
        }
        catch (NumberFormatException malformed) {
            throw new GrantForgeException(CommonErrorCode.NOT_FOUND, "no session " + id, malformed);
        }
    }

    private static void invalidateIf(boolean current, HttpServletRequest request)
    {
        if (current) {
            HttpSession session = request.getSession(false);
            if (session != null) {
                session.invalidate();
            }
            SecurityContextHolder.clearContext();
        }
    }
}

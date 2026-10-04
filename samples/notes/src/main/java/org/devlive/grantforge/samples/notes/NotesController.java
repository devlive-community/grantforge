// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.samples.notes;

import jakarta.servlet.http.HttpServletRequest;
import org.devlive.grantforge.sdk.GrantForge;
import org.devlive.grantforge.sdk.RequirePermission;
import org.devlive.grantforge.sdk.UserAuthorization;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.view.RedirectView;
import org.springframework.web.util.HtmlUtils;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static java.util.Objects.requireNonNull;

/**
 * The notes pages, rendered on the server: the form to write a note appears for users GrantForge lets use it, and
 * writing needs the API permission notes.write either way.
 */
@RestController
public class NotesController
{
    private final GrantForge grantForge;
    private final List<String> notes = new CopyOnWriteArrayList<>();

    /**
     * Creates the controller.
     *
     * @param grantForge what the user may do
     */
    public NotesController(GrantForge grantForge)
    {
        this.grantForge = requireNonNull(grantForge, "grantForge");
    }

    /**
     * Shows the notes, or a link to sign in.
     *
     * @param request the request, for its CSRF token
     * @return the page
     */
    @GetMapping(value = "/", produces = MediaType.TEXT_HTML_VALUE)
    public String home(HttpServletRequest request)
    {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof OAuth2AuthenticationToken signedIn)) {
            return page("<a id=\"sign-in\" href=\"/oauth2/authorization/grantforge\">Sign in with GrantForge</a>");
        }
        UserAuthorization user = grantForge.current();
        StringBuilder body = new StringBuilder("<p id=\"user\">Signed in as ").append(HtmlUtils.htmlEscape(user.username()))
                .append(" (").append(HtmlUtils.htmlEscape(String.valueOf(signedIn.getPrincipal().getAttributes().get("name")))).append(")</p>");
        if (user.hasResource("notes.btn.write")) {
            CsrfToken csrf = (CsrfToken) requireNonNull(request.getAttribute(CsrfToken.class.getName()), "csrf");
            body.append("<form id=\"write\" method=\"post\" action=\"/notes\"><input name=\"text\" aria-label=\"Note\" required>")
                    .append("<input type=\"hidden\" name=\"").append(csrf.getParameterName()).append("\" value=\"").append(csrf.getToken())
                    .append("\"><button type=\"submit\">Write note</button></form>");
        }
        body.append("<ul id=\"notes\">");
        notes.forEach(note -> body.append("<li>").append(HtmlUtils.htmlEscape(note)).append("</li>"));
        return page(body.append("</ul>").toString());
    }

    /**
     * Writes a note.
     *
     * @param text the note
     * @return back to the notes
     */
    @RequirePermission("notes.write")
    @PostMapping("/notes")
    public RedirectView write(@RequestParam String text)
    {
        notes.add(grantForge.current().username() + ": " + text);
        return new RedirectView("/");
    }

    private static String page(String body)
    {
        return "<!doctype html><html lang=\"en\"><head><meta charset=\"utf-8\"><title>Sample notes</title></head><body><h1>Sample notes</h1>"
                + body + "</body></html>";
    }
}

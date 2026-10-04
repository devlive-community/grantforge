// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.sdk;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Answers {@link GrantForgeException}s as RFC 9457 problem details with their status, without the reason's details,
 * which go to the logs. An application with its own error format declares its own handler instead.
 */
@RestControllerAdvice
public class GrantForgeProblems
{
    /**
     * Turns a refusal into a problem.
     *
     * @param refused the refusal
     * @return the problem
     */
    @ExceptionHandler(GrantForgeException.class)
    public ProblemDetail refused(GrantForgeException refused)
    {
        HttpStatus status = HttpStatus.valueOf(refused.getReason().status());
        ProblemDetail problem = ProblemDetail.forStatus(status);
        problem.setTitle(status.getReasonPhrase());
        problem.setProperty("reason", refused.getReason().name());
        return problem;
    }
}

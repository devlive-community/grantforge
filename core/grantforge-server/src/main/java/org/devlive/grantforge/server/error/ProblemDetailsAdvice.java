// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.error;

import jakarta.servlet.http.HttpServletRequest;
import org.devlive.grantforge.common.error.CommonErrorCode;
import org.devlive.grantforge.common.error.ErrorCode;
import org.devlive.grantforge.common.error.GrantForgeException;
import org.devlive.grantforge.server.web.RequestIdFilter;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static java.util.Objects.requireNonNullElse;

/**
 * Renders every failure as an RFC 9457 problem detail.
 *
 * <p>Each body carries three extension members clients rely on: {@code code} (stable
 * {@link ErrorCode#code()}), {@code messageKey} (i18n key for the localised message) and
 * {@code requestId} (correlates the response with server logs). Validation failures add
 * {@code errors}, a list of {@code field}/{@code message} pairs. For 5xx responses the detail is a fixed
 * generic text: exception messages may contain internal information and are only logged.
 */
@RestControllerAdvice
public class ProblemDetailsAdvice
        extends ResponseEntityExceptionHandler
{
    /** Extension member with the stable error code. */
    public static final String CODE = "code";
    /** Extension member with the i18n message key. */
    public static final String MESSAGE_KEY = "messageKey";
    /** Extension member with the request correlation ID. */
    public static final String REQUEST_ID = "requestId";
    /** Extension member listing field validation errors. */
    public static final String ERRORS = "errors";

    static final String INTERNAL_DETAIL = "An unexpected error occurred. Quote the request ID when reporting it.";

    private static final Logger LOG = LoggerFactory.getLogger(ProblemDetailsAdvice.class);

    /**
     * Handles business failures raised with an {@link ErrorCode}.
     *
     * @param error the failure
     * @param request the current request
     * @return the problem response with the error's HTTP status
     */
    @ExceptionHandler(GrantForgeException.class)
    public ResponseEntity<ProblemDetail> handleGrantForge(GrantForgeException error, HttpServletRequest request)
    {
        ErrorCode code = error.getErrorCode();
        boolean serverError = code.httpStatus() >= 500;
        if (serverError) {
            LOG.error("Request failed with {}", code.code(), error);
        }
        else {
            LOG.debug("Request rejected with {}: {}", code.code(), error.getMessage());
        }
        String detail = serverError ? INTERNAL_DETAIL : requireNonNullElse(error.getMessage(), code.code());
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatusCode.valueOf(code.httpStatus()), detail);
        return ResponseEntity.status(code.httpStatus()).body(enrich(problem, code, RequestIdFilter.currentId(request)));
    }

    /**
     * Last-resort handler for exceptions nothing else handled; never exposes the exception message.
     *
     * @param error the failure
     * @param request the current request
     * @return a 500 problem response
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleUnexpected(Exception error, HttpServletRequest request)
    {
        LOG.error("Unhandled exception", error);
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatusCode.valueOf(CommonErrorCode.INTERNAL.httpStatus()), INTERNAL_DETAIL);
        return ResponseEntity.internalServerError()
                .body(enrich(problem, CommonErrorCode.INTERNAL, RequestIdFilter.currentId(request)));
    }

    @Override
    protected @Nullable ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException error,
            HttpHeaders headers, HttpStatusCode status, WebRequest request)
    {
        // Field errors let the web console highlight the exact inputs; values are never echoed back.
        List<Map<String, String>> errors = error.getBindingResult().getFieldErrors().stream()
                .map(ProblemDetailsAdvice::fieldError)
                .toList();
        error.getBody().setProperty(ERRORS, errors);
        return super.handleMethodArgumentNotValid(error, headers, status, request);
    }

    @Override
    protected @Nullable ResponseEntity<Object> handleExceptionInternal(Exception error, @Nullable Object body,
            HttpHeaders headers, HttpStatusCode status, WebRequest request)
    {
        ResponseEntity<Object> response = super.handleExceptionInternal(error, body, headers, status, request);
        if (response != null && response.getBody() instanceof ProblemDetail problem) {
            enrich(problem, CommonErrorCode.forStatus(status.value()), requestId(request));
        }
        return response;
    }

    private static ProblemDetail enrich(ProblemDetail problem, ErrorCode code, @Nullable String requestId)
    {
        problem.setProperty(CODE, code.code());
        problem.setProperty(MESSAGE_KEY, code.messageKey());
        if (requestId != null) {
            problem.setProperty(REQUEST_ID, requestId);
        }
        return problem;
    }

    private static @Nullable String requestId(WebRequest request)
    {
        if (request instanceof NativeWebRequest nativeRequest) {
            HttpServletRequest servletRequest = nativeRequest.getNativeRequest(HttpServletRequest.class);
            return servletRequest == null ? null : RequestIdFilter.currentId(servletRequest);
        }
        return null;
    }

    private static Map<String, String> fieldError(FieldError error)
    {
        Map<String, String> entry = new LinkedHashMap<>();
        entry.put("field", error.getField());
        entry.put("message", requireNonNullElse(error.getDefaultMessage(), "invalid"));
        return entry;
    }
}

// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.common.error;

/** Errors shared by every module; feature modules define their own {@link ErrorCode} enums. */
public enum CommonErrorCode
        implements ErrorCode
{
    /** The request is malformed or a parameter is invalid. */
    BAD_REQUEST("GF-COMMON-400", 400, "error.common.bad-request"),
    /** No valid session or token was presented. */
    UNAUTHENTICATED("GF-COMMON-401", 401, "error.common.unauthenticated"),
    /** The caller is authenticated but not allowed to perform the operation. */
    FORBIDDEN("GF-COMMON-403", 403, "error.common.forbidden"),
    /** The resource does not exist or is outside the caller's data scope. */
    NOT_FOUND("GF-COMMON-404", 404, "error.common.not-found"),
    /** The HTTP method is not supported for the resource. */
    METHOD_NOT_ALLOWED("GF-COMMON-405", 405, "error.common.method-not-allowed"),
    /** The request conflicts with the current state, for example a duplicate code or a stale version. */
    CONFLICT("GF-COMMON-409", 409, "error.common.conflict"),
    /** The request body type is not supported. */
    UNSUPPORTED_MEDIA_TYPE("GF-COMMON-415", 415, "error.common.unsupported-media-type"),
    /** An unexpected failure; details are logged with the request ID and never returned. */
    INTERNAL("GF-COMMON-500", 500, "error.common.internal");

    private final String code;
    private final int httpStatus;
    private final String messageKey;

    CommonErrorCode(String code, int httpStatus, String messageKey)
    {
        this.code = code;
        this.httpStatus = httpStatus;
        this.messageKey = messageKey;
    }

    @Override
    public String code()
    {
        return code;
    }

    @Override
    public int httpStatus()
    {
        return httpStatus;
    }

    @Override
    public String messageKey()
    {
        return messageKey;
    }

    /**
     * Returns the common error for an HTTP status, falling back to {@link #BAD_REQUEST} for other
     * 4xx statuses and {@link #INTERNAL} for everything else.
     *
     * @param status an HTTP status code
     * @return the matching error code; never {@code null}
     */
    public static CommonErrorCode forStatus(int status)
    {
        for (CommonErrorCode candidate : values()) {
            if (candidate.httpStatus == status) {
                return candidate;
            }
        }
        return status >= 400 && status < 500 ? BAD_REQUEST : INTERNAL;
    }
}

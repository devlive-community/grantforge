// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.service;

import org.devlive.grantforge.common.error.ErrorCode;

/**
 * Errors of service management; messages live in {@code i18n/service*.properties}.
 *
 * <p>A failure the operator can act on is a 4xx, never a 5xx: the console names the reason of a problem below
 * 500 only, and answers anything above with a generic text and the request ID instead. So the target system of
 * a data service refusing a lookup, being unreachable or rejecting the lookup user is a failed dependency of
 * GrantForge, not a fault of its own, while a plugin that failed or did not answer stays a 5xx.
 */
public enum ServiceErrorCode
        implements ErrorCode
{
    /** Another service of the tenant has the name; argument: the name. */
    NAME_TAKEN("GF-SERVICE-001", 409, "error.service.name-taken"),
    /** No active plugin provides the service type; argument: the type. */
    TYPE_UNAVAILABLE("GF-SERVICE-002", 409, "error.service.type-unavailable"),
    /** Some configuration values are wrong; the field issues say which. */
    CONFIG_INVALID("GF-SERVICE-003", 400, "error.service.config-invalid"),
    /** The resource level does not offer lookups; argument: the level. */
    LOOKUP_UNSUPPORTED("GF-SERVICE-004", 400, "error.service.lookup-unsupported"),
    /** The plugin failed; argument: what the plugin said, without secrets. */
    PLUGIN_FAILED("GF-SERVICE-005", 502, "error.service.plugin-failed"),
    /** Some parts of a policy are wrong; the field issues say which. */
    POLICY_INVALID("GF-SERVICE-006", 400, "error.service.policy-invalid"),
    /** Another policy of the service has the name; argument: the name. */
    POLICY_NAME_TAKEN("GF-SERVICE-007", 409, "error.service.policy-name-taken"),
    /** The place a lookup starts from does not exist; argument: what the plugin said. */
    LOOKUP_NOT_FOUND("GF-SERVICE-008", 404, "error.service.lookup-not-found"),
    /** The target system refused the lookup user; argument: what the plugin said. */
    LOOKUP_DENIED("GF-SERVICE-009", 424, "error.service.lookup-denied"),
    /** The target system could not be reached; argument: what the plugin said. */
    LOOKUP_UNREACHABLE("GF-SERVICE-010", 424, "error.service.lookup-unreachable"),
    /** Signing in to the target system failed; argument: what the plugin said. */
    LOOKUP_AUTHENTICATION_FAILED("GF-SERVICE-011", 424, "error.service.lookup-authentication-failed"),
    /** There were more values than the plugin may read; argument: what the plugin said. */
    LOOKUP_LIMIT_EXCEEDED("GF-SERVICE-012", 422, "error.service.lookup-limit-exceeded"),
    /** What was typed cannot be looked up; argument: what the plugin said. */
    LOOKUP_INVALID_INPUT("GF-SERVICE-013", 400, "error.service.lookup-invalid-input"),
    /** The plugin did not answer within the time limit of plugin calls. */
    PLUGIN_TIMED_OUT("GF-SERVICE-014", 504, "error.service.plugin-timed-out");

    private final String code;
    private final int httpStatus;
    private final String messageKey;

    ServiceErrorCode(String code, int httpStatus, String messageKey)
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
}

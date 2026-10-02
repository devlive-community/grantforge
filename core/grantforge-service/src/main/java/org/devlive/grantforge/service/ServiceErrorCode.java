// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.service;

import org.devlive.grantforge.common.error.ErrorCode;

/** Errors of service management; messages live in {@code i18n/service*.properties}. */
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
    /** The plugin failed or did not answer in time. */
    PLUGIN_FAILED("GF-SERVICE-005", 502, "error.service.plugin-failed"),
    /** Some parts of a policy are wrong; the field issues say which. */
    POLICY_INVALID("GF-SERVICE-006", 400, "error.service.policy-invalid"),
    /** Another policy of the service has the name; argument: the name. */
    POLICY_NAME_TAKEN("GF-SERVICE-007", 409, "error.service.policy-name-taken");

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

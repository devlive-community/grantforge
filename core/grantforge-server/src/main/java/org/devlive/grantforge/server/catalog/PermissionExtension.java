// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.catalog;

import io.swagger.v3.oas.models.Operation;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;

/**
 * Publishes who may call each operation as the {@code x-permission} extension of the OpenAPI document:
 * {@code public}, {@code authenticated} or the permission code. The console's permission manifest is checked
 * against it.
 */
@Component
public final class PermissionExtension
        implements OperationCustomizer
{
    /** Name of the extension. */
    public static final String NAME = "x-permission";

    @Override
    public Operation customize(Operation operation, HandlerMethod handler)
    {
        operation.addExtension(NAME, EndpointDeclarations.extension(EndpointDeclarations.of(handler)));
        return operation;
    }
}

// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.catalog;

import io.swagger.v3.oas.models.Operation;
import org.devlive.grantforge.common.security.RequirePermission;
import org.junit.jupiter.api.Test;
import org.springframework.web.method.HandlerMethod;

import static org.assertj.core.api.Assertions.assertThat;

class PermissionExtensionTest
{
    /** A controller with one protected method. */
    static final class Controller
    {
        @RequirePermission("system.user.read")
        public void list()
        {
        }
    }

    @Test
    void addsTheExtensionToTheOperation() throws NoSuchMethodException
    {
        Operation operation = new PermissionExtension().customize(new Operation(),
                new HandlerMethod(new Controller(), Controller.class.getMethod("list")));

        assertThat(operation.getExtensions()).containsEntry(PermissionExtension.NAME, "system.user.read");
    }
}

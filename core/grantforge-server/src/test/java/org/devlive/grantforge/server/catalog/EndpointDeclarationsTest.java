// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.catalog;

import org.devlive.grantforge.authz.domain.ApiEndpoint;
import org.devlive.grantforge.authz.domain.EndpointAccess;
import org.devlive.grantforge.common.security.AuthenticatedEndpoint;
import org.devlive.grantforge.common.security.PublicEndpoint;
import org.devlive.grantforge.common.security.RequirePermission;
import org.junit.jupiter.api.Test;
import org.springframework.web.method.HandlerMethod;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EndpointDeclarationsTest
{
    /** A controller whose methods override its class-level declaration, or forget one. */
    @AuthenticatedEndpoint
    static final class Annotated
    {
        public void inherited()
        {
        }

        @RequirePermission("system.user.read")
        public void read()
        {
        }

        @PublicEndpoint
        public void open()
        {
        }

        @PublicEndpoint
        @AuthenticatedEndpoint
        public void both()
        {
        }
    }

    /** A controller without any declaration. */
    static final class Bare
    {
        public void forgotten()
        {
        }
    }

    private static HandlerMethod handler(Object bean, String method) throws NoSuchMethodException
    {
        return new HandlerMethod(bean, bean.getClass().getMethod(method));
    }

    @Test
    void methodsWinOverTheirControllerAndNeedExactlyOneDeclaration() throws NoSuchMethodException
    {
        Annotated bean = new Annotated();

        assertThat(EndpointDeclarations.of(handler(bean, "inherited")))
                .isEqualTo(new ApiEndpoint.Declaration("Annotated#inherited", EndpointAccess.AUTHENTICATED, null));
        assertThat(EndpointDeclarations.of(handler(bean, "read")))
                .isEqualTo(new ApiEndpoint.Declaration("Annotated#read", EndpointAccess.PERMISSION, "system.user.read"));
        assertThat(EndpointDeclarations.of(handler(bean, "open")).access()).isEqualTo(EndpointAccess.PUBLIC);
        assertThatThrownBy(() -> EndpointDeclarations.of(handler(bean, "both")))
                .hasMessageStartingWith("Annotated#both has more than one access annotation");
        assertThatThrownBy(() -> EndpointDeclarations.of(handler(new Bare(), "forgotten")))
                .hasMessageStartingWith("Bare#forgotten has no access annotation");
    }

    @Test
    void extensionsNameTheAccessOrThePermission()
    {
        assertThat(EndpointDeclarations.extension(new ApiEndpoint.Declaration("A#b", EndpointAccess.PUBLIC, null))).isEqualTo("public");
        assertThat(EndpointDeclarations.extension(new ApiEndpoint.Declaration("A#b", EndpointAccess.AUTHENTICATED, null)))
                .isEqualTo("authenticated");
        assertThat(EndpointDeclarations.extension(new ApiEndpoint.Declaration("A#b", EndpointAccess.PERMISSION, "system.user.read")))
                .isEqualTo("system.user.read");
    }
}

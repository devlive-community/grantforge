// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.sdk;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RequirePermissionInterceptorTest
{
    private final GrantForgeClient client = mock(GrantForgeClient.class);
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new Orders(), new Reports())
            .addInterceptors(new RequirePermissionInterceptor(new GrantForge(client, new BearerTokenResolver())))
            .setControllerAdvice(new GrantForgeProblems()).build();

    @Test
    void runsHandlersOnlyForUsersWithTheirPermissions() throws Exception
    {
        when(client.authorization("t")).thenReturn(SdkTestData.ada());

        mvc.perform(get("/orders").header(HttpHeaders.AUTHORIZATION, "Bearer t")).andExpect(status().isOk());
        mvc.perform(get("/orders/delete").header(HttpHeaders.AUTHORIZATION, "Bearer t")).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.reason").value("FORBIDDEN"));
        mvc.perform(get("/orders")).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.status").value(401));
        mvc.perform(get("/open")).andExpect(status().isOk());
        // On the controller, for every method.
        mvc.perform(get("/reports").header(HttpHeaders.AUTHORIZATION, "Bearer t")).andExpect(status().isForbidden());
    }

    /** A controller with guarded and open methods. */
    @RestController
    static class Orders
    {
        @RequirePermission("orders.read")
        @GetMapping("/orders")
        String orders()
        {
            return "orders";
        }

        @RequirePermission({"orders.read", "orders.delete"})
        @GetMapping("/orders/delete")
        String delete()
        {
            return "deleted";
        }

        @GetMapping("/open")
        String open()
        {
            return "open";
        }
    }

    /** A controller guarded as a whole. */
    @RestController
    @RequirePermission("reports.read")
    static class Reports
    {
        @GetMapping("/reports")
        String reports()
        {
            return "reports";
        }
    }
}

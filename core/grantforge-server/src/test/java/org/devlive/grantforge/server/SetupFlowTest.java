// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** First-run setup through the assembled server: bootstrap state, localized errors and the one-time rule. */
@SpringBootTest(properties = {
        // A database of its own: completing setup must not leak into other test contexts.
        "spring.datasource.url=jdbc:h2:mem:setup-flow",
        "grantforge.setup.token=" + SetupFlowTest.TOKEN,
})
@AutoConfigureMockMvc
class SetupFlowTest
{
    static final String TOKEN = "setup-flow-token-0123456789";

    @Autowired
    private MockMvc mvc;

    private ResultActions submit(String language, String body) throws Exception
    {
        return mvc.perform(post("/api/v1/setup").with(csrf()).header(HttpHeaders.ACCEPT_LANGUAGE, language)
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private static String setup(String token, String password)
    {
        return """
                {"token": "%s", "tenantName": "Acme", "username": "admin", "password": "%s"}
                """.formatted(token, password);
    }

    @Test
    void setupRunsExactlyOnce() throws Exception
    {
        mvc.perform(get("/api/v1/bootstrap"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.setupRequired").value(true))
                .andExpect(jsonPath("$.registrationEnabled").value(false));

        submit("zh-CN", setup("wrong-token", "a long enough password"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("GF-IDENTITY-002"))
                .andExpect(jsonPath("$.detail").value("初始化令牌无效，请使用服务端最近一次日志中输出的令牌。"));
        submit("en", setup(TOKEN, "short"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("GF-IDENTITY-010"))
                .andExpect(jsonPath("$.detail").value("The password must have at least 12 characters."));
        submit("en", "{\"token\": \"\", \"username\": \"a b\", \"password\": \"\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("GF-COMMON-400"))
                .andExpect(jsonPath("$.errors.length()").value(3));

        submit("en", setup(TOKEN, "a long enough password"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tenantCode").value("default"))
                .andExpect(jsonPath("$.username").value("admin"));

        mvc.perform(get("/api/v1/bootstrap")).andExpect(jsonPath("$.setupRequired").value(false));
        submit("en", setup(TOKEN, "a long enough password"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("GF-IDENTITY-001"));
    }
}

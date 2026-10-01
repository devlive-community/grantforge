// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.org;

import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import org.devlive.grantforge.identity.domain.UserAccount;
import org.devlive.grantforge.identity.domain.UserAccountRepository;
import org.devlive.grantforge.persistence.tenant.TenantContext;
import org.devlive.grantforge.server.security.SecurityConfiguration;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Instant;

import static java.util.Objects.requireNonNull;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The organization tree through the assembled server. */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:org-flow",
        "grantforge.setup.token=" + OrgControllerTest.TOKEN,
})
@AutoConfigureMockMvc
class OrgControllerTest
{
    static final String TOKEN = "org-flow-token-0123456789";
    private static final String PASSWORD = "a long enough password";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private UserAccountRepository accounts;

    @Autowired
    private PasswordEncoder encoder;

    @BeforeEach
    void setUp() throws Exception
    {
        Boolean required = jdbc.queryForObject("SELECT COUNT(*) = 0 FROM gf_platform_setting WHERE setting_key = ?",
                Boolean.class, "setup.completed-at");
        if (Boolean.TRUE.equals(required)) {
            mvc.perform(post("/api/v1/setup").with(csrf()).contentType(MediaType.APPLICATION_JSON).content("""
                    {"token": "%s", "username": "admin", "password": "%s"}
                    """.formatted(TOKEN, PASSWORD))).andExpect(status().isOk());
            long tenant = requireNonNull(jdbc.queryForObject("SELECT id FROM gf_tenant", Long.class));
            TenantContext.runInTenant(tenant, () -> accounts.save(UserAccount.create("reader", encoder.encode(PASSWORD),
                    Instant.now())));
        }
        jdbc.update("UPDATE gf_org_unit SET parent_id = NULL");
        jdbc.update("DELETE FROM gf_org_unit");
    }

    private Cookie login(String username) throws Exception
    {
        return requireNonNull(mvc.perform(post("/api/v1/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\": \"%s\", \"password\": \"%s\"}".formatted(username, PASSWORD)))
                .andExpect(status().isOk()).andReturn().getResponse().getCookie(SecurityConfiguration.SESSION_COOKIE));
    }

    private ResultActions create(Cookie session, @Nullable String parentId, String code) throws Exception
    {
        String parent = parentId == null ? "null" : "\"" + parentId + "\"";
        return mvc.perform(post("/api/v1/org-units").with(csrf()).cookie(session).header(HttpHeaders.ACCEPT_LANGUAGE, "zh-CN")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"parentId\": %s, \"code\": \"%s\", \"name\": \"%s 部\"}".formatted(parent, code, code)));
    }

    private String idOf(ResultActions created) throws Exception
    {
        return JsonPath.read(created.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
    }

    @Test
    void administratorsShapeTheTreeAndEveryoneReadsIt() throws Exception
    {
        Cookie admin = login("admin");
        String hq = idOf(create(admin, null, "hq"));
        String sales = idOf(create(admin, hq, "sales"));
        String east = idOf(create(admin, sales, "east"));
        String lab = idOf(create(admin, null, "lab"));

        mvc.perform(get("/api/v1/org-units").cookie(login("reader")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(4))
                .andExpect(jsonPath("$[0].code").value("hq"))
                .andExpect(jsonPath("$[0].parentId").doesNotExist())
                .andExpect(jsonPath("$[2].parentId").value(hq))
                .andExpect(jsonPath("$[3].depth").value(2));
        create(login("reader"), null, "x").andExpect(status().isForbidden());
        create(admin, null, "HQ").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("GF-IDENTITY-040"))
                .andExpect(jsonPath("$.detail").value("部门编码“hq”已被使用。"));

        mvc.perform(post("/api/v1/org-units/" + hq + "/move").with(csrf()).cookie(admin).header(HttpHeaders.ACCEPT_LANGUAGE, "zh-CN")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"parentId\": \"%s\", \"position\": 0}".formatted(east)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("部门不能移动到自身或其下级部门之下。"));
        mvc.perform(post("/api/v1/org-units/" + sales + "/move").with(csrf()).cookie(admin)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"parentId\": \"%s\", \"position\": 0}".formatted(lab)))
                .andExpect(jsonPath("$.parentId").value(lab))
                .andExpect(jsonPath("$.depth").value(1));
        mvc.perform(put("/api/v1/org-units/" + lab).with(csrf()).cookie(admin).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\": \"lab\", \"name\": \"研究院\"}"))
                .andExpect(jsonPath("$.name").value("研究院"));

        mvc.perform(delete("/api/v1/org-units/" + lab).with(csrf()).cookie(admin))
                .andExpect(jsonPath("$.code").value("GF-IDENTITY-043"));
        mvc.perform(delete("/api/v1/org-units/" + east).with(csrf()).cookie(admin)).andExpect(status().isNoContent());
        mvc.perform(delete("/api/v1/org-units/bogus").with(csrf()).cookie(admin)).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/org-units").cookie(admin)).andExpect(jsonPath("$.length()").value(3));
    }

    @Test
    void requestsAreValidated() throws Exception
    {
        Cookie admin = login("admin");

        mvc.perform(post("/api/v1/org-units").with(csrf()).cookie(admin).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\": \"\", \"name\": \"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.length()").value(2));
        create(admin, "not-an-id", "x").andExpect(status().isNotFound());
    }
}

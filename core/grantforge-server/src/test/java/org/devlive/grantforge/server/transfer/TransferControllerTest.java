// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.transfer;

import jakarta.servlet.http.Cookie;
import org.devlive.grantforge.server.security.SecurityConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** CSV export and import through the assembled server. */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:transfer-flow",
        "grantforge.setup.token=" + TransferControllerTest.TOKEN,
})
@AutoConfigureMockMvc
class TransferControllerTest
{
    static final String TOKEN = "transfer-flow-token-0123456789";
    private static final String PASSWORD = "a long enough password";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    private Cookie admin;

    @BeforeEach
    void setUp() throws Exception
    {
        Boolean required = jdbc.queryForObject("SELECT COUNT(*) = 0 FROM gf_platform_setting WHERE setting_key = ?",
                Boolean.class, "setup.completed-at");
        if (Boolean.TRUE.equals(required)) {
            mvc.perform(post("/api/v1/setup").with(csrf()).contentType(MediaType.APPLICATION_JSON).content("""
                    {"token": "%s", "username": "admin", "password": "%s"}
                    """.formatted(TOKEN, PASSWORD))).andExpect(status().isOk());
        }
        jdbc.update("DELETE FROM gf_org_member");
        jdbc.update("DELETE FROM gf_user_account WHERE system_account = ?", false);
        jdbc.update("UPDATE gf_org_unit SET parent_id = NULL");
        jdbc.update("DELETE FROM gf_org_unit");
        admin = requireNonNull(mvc.perform(post("/api/v1/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\": \"admin\", \"password\": \"%s\"}".formatted(PASSWORD)))
                .andReturn().getResponse().getCookie(SecurityConfiguration.SESSION_COOKIE));
    }

    private ResultActions upload(String path, byte[] content, boolean apply) throws Exception
    {
        return mvc.perform(multipart(path).file(new MockMultipartFile("file", "data.csv", "text/csv", content))
                .param("apply", Boolean.toString(apply)).with(csrf()).cookie(admin)
                .header(HttpHeaders.ACCEPT_LANGUAGE, "zh-CN"));
    }

    @Test
    void importsDepartmentsThenAccountsAndExportsThem() throws Exception
    {
        byte[] departments = "code,name,parentCode\nsales,销售部,hq\nhq,总部,\n".getBytes(StandardCharsets.UTF_8);
        upload("/api/v1/org-units/import", departments, false)
                .andExpect(jsonPath("$.rows").value(2))
                .andExpect(jsonPath("$.applied").value(false))
                .andExpect(jsonPath("$.problems.length()").value(0));
        upload("/api/v1/org-units/import", departments, true)
                .andExpect(jsonPath("$.created").value(2))
                .andExpect(jsonPath("$.applied").value(true));

        byte[] accounts = ("username,password,displayName,primaryUnit\n" + "ivy," + PASSWORD + ",艾薇,sales\n")
                .getBytes(StandardCharsets.UTF_8);
        upload("/api/v1/users/import", accounts, true).andExpect(jsonPath("$.created").value(1));
        upload("/api/v1/users/import", accounts, true)
                .andExpect(jsonPath("$.applied").value(false))
                .andExpect(jsonPath("$.problems[0].row").value(2))
                .andExpect(jsonPath("$.problems[0].column").value("username"))
                .andExpect(jsonPath("$.problems[0].code").value("GF-IDENTITY-031"))
                .andExpect(jsonPath("$.problems[0].message").value("用户名“ivy”已被使用。"));

        byte[] users = mvc.perform(get("/api/v1/users/export").param("q", "ivy").cookie(admin))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "text/csv;charset=UTF-8"))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                        org.hamcrest.Matchers.startsWith("attachment; filename=\"users-")))
                .andReturn().getResponse().getContentAsByteArray();
        assertThat(new String(users, StandardCharsets.UTF_8)).startsWith("﻿username,displayName,email,status")
                .contains("ivy,艾薇,,ACTIVE,sales,,,");
        String tree = mvc.perform(get("/api/v1/org-units/export").cookie(admin)).andReturn().getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        assertThat(tree).contains("hq,总部,,0\r\nsales,销售部,hq,0\r\n");
    }

    @Test
    void readsSpreadsheetsSavedInGbk() throws Exception
    {
        byte[] gbk = "code,name\nlab,实验室\n".getBytes(Charset.forName("GBK"));

        upload("/api/v1/org-units/import", gbk, true).andExpect(jsonPath("$.created").value(1));
        assertThat(jdbc.queryForObject("SELECT name FROM gf_org_unit WHERE code = 'lab'", String.class)).isEqualTo("实验室");
    }

    @Test
    void rejectsBrokenFilesAsAWhole() throws Exception
    {
        upload("/api/v1/org-units/import", "code,name\nx,\"open\n".getBytes(StandardCharsets.UTF_8), false)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("GF-IDENTITY-090"));
        upload("/api/v1/users/import", "username\nx\n".getBytes(StandardCharsets.UTF_8), false)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("文件缺少必需的列“password”。"));
        upload("/api/v1/users/import", new byte[0], false)
                .andExpect(jsonPath("$.code").value("GF-IDENTITY-093"));
        mvc.perform(get("/api/v1/users/export").param("unitId", "nope").cookie(admin)).andExpect(status().isNotFound());
    }

    @Test
    void onlyAdministratorsTransfer() throws Exception
    {
        upload("/api/v1/users/import", ("username,password\nwill," + PASSWORD + "\n").getBytes(StandardCharsets.UTF_8), true)
                .andExpect(jsonPath("$.created").value(1));
        jdbc.update("UPDATE gf_user_account SET must_change_password = ? WHERE username_norm = 'will'", false);
        Cookie will = requireNonNull(mvc.perform(post("/api/v1/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\": \"will\", \"password\": \"%s\"}".formatted(PASSWORD)))
                .andReturn().getResponse().getCookie(SecurityConfiguration.SESSION_COOKIE));

        mvc.perform(get("/api/v1/org-units/export").cookie(will)).andExpect(status().isForbidden());
        mvc.perform(multipart("/api/v1/org-units/import").file(new MockMultipartFile("file", "x.csv", "text/csv",
                "code,name\nx,X\n".getBytes(StandardCharsets.UTF_8))).with(csrf()).cookie(will)).andExpect(status().isForbidden());
    }
}

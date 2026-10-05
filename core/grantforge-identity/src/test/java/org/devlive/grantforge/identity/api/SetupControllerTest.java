// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.identity.api;

import org.devlive.grantforge.identity.application.IdentitySourceService;
import org.devlive.grantforge.identity.application.RegistrationService;
import org.devlive.grantforge.identity.application.SecurityProperties;
import org.devlive.grantforge.identity.application.SetupCommand;
import org.devlive.grantforge.identity.application.SetupResult;
import org.devlive.grantforge.identity.application.SetupService;
import org.devlive.grantforge.identity.application.SignInOption;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SetupController.class)
@Import(SetupControllerTest.Settings.class)
class SetupControllerTest
{
    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private SetupService setup;

    @MockitoBean
    private RegistrationService registration;

    @MockitoBean
    private IdentitySourceService identitySources;

    @Test
    void bootstrapReportsSetupAndRegistrationState() throws Exception
    {
        when(setup.isRequired()).thenReturn(true);
        when(identitySources.signInOptions()).thenReturn(List.of(new SignInOption("okta", "Okta")));

        mvc.perform(get("/api/v1/bootstrap"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.setupRequired").value(true))
                .andExpect(jsonPath("$.registrationEnabled").value(true))
                .andExpect(jsonPath("$.signInSources[0].code").value("okta"))
                .andExpect(jsonPath("$.signInSources[0].name").value("Okta"));
    }

    @Test
    void setupPassesTheTrimmedNameToTheService() throws Exception
    {
        SetupCommand expected = new SetupCommand("t", "Acme", "admin", "a long password", null);
        when(setup.complete(expected)).thenReturn(new SetupResult("default", "admin"));

        mvc.perform(post("/api/v1/setup").contentType(MediaType.APPLICATION_JSON).content("""
                        {"token": "t", "tenantName": "Acme", "username": " admin ", "password": "a long password"}
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tenantCode").value("default"))
                .andExpect(jsonPath("$.username").value("admin"));
    }

    @Test
    void invalidRequestsNeverReachTheService() throws Exception
    {
        mvc.perform(post("/api/v1/setup").contentType(MediaType.APPLICATION_JSON).content("""
                        {"token": "", "username": "a b", "password": ""}
                        """))
                .andExpect(status().isBadRequest());

        verify(setup, never()).complete(any());
    }

    @Test
    void registrationPassesTheTrimmedNameToTheService() throws Exception
    {
        when(registration.register("visitor", "a long password", "Visitor")).thenReturn("visitor");

        mvc.perform(post("/api/v1/register").contentType(MediaType.APPLICATION_JSON).content("""
                        {"username": " visitor ", "password": "a long password", "displayName": "Visitor"}
                        """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value("visitor"));
        mvc.perform(post("/api/v1/register").contentType(MediaType.APPLICATION_JSON).content("""
                        {"username": "x", "password": ""}
                        """))
                .andExpect(status().isBadRequest());
        verify(registration, never()).register(eq("x"), any(), any());
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class Settings
    {
        @Bean
        SecurityProperties securityProperties()
        {
            return new SecurityProperties(true, SecurityProperties.Password.defaults(), SecurityProperties.Lockout.defaults());
        }
    }
}

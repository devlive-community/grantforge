// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.error;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.devlive.grantforge.common.error.CommonErrorCode;
import org.devlive.grantforge.common.error.ErrorCode;
import org.devlive.grantforge.common.error.FieldIssue;
import org.devlive.grantforge.common.error.GrantForgeException;
import org.devlive.grantforge.server.web.RequestIdFilter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.ErrorResponseException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.context.request.WebRequest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(ProblemDetailsAdviceTest.FailingController.class)
class ProblemDetailsAdviceTest
{
    @Autowired
    private MockMvc mvc;

    @Test
    void businessErrorKeepsItsStatusCodeAndDetail() throws Exception
    {
        mvc.perform(get("/test/errors/not-found").header(RequestIdFilter.HEADER, "req-1"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.detail").value("The requested resource does not exist."))
                .andExpect(jsonPath("$.code").value("GF-COMMON-404"))
                .andExpect(jsonPath("$.messageKey").value("error.common.not-found"))
                .andExpect(jsonPath("$.requestId").value("req-1"));
    }

    @Test
    void detailFollowsTheRequestLanguage() throws Exception
    {
        mvc.perform(get("/test/errors/not-found").header(HttpHeaders.ACCEPT_LANGUAGE, "zh-CN,zh;q=0.9"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("\u8bf7\u6c42\u7684\u8d44\u6e90\u4e0d\u5b58\u5728\u3002"));
        mvc.perform(get("/test/errors/not-found").header(HttpHeaders.ACCEPT_LANGUAGE, "fr-FR"))
                .andExpect(jsonPath("$.detail").value("The requested resource does not exist."));
        mvc.perform(post("/test/errors/validated").with(csrf()).header(HttpHeaders.ACCEPT_LANGUAGE, "zh-CN")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"\"}"))
                .andExpect(jsonPath("$.detail").value("\u8bf7\u6c42\u53c2\u6570\u4e0d\u6b63\u786e\u3002"));
    }

    @Test
    void unknownKeysFallBackToTheDeveloperText()
    {
        ErrorCode custom = new ErrorCode()
        {
            @Override
            public String code()
            {
                return "GF-TEST-400";
            }

            @Override
            public int httpStatus()
            {
                return 400;
            }

            @Override
            public String messageKey()
            {
                return "error.test.missing";
            }
        };

        assertThat(new ProblemDetailsAdvice().localize(custom, new Object[0], "fallback")).isEqualTo("fallback");
    }

    @Test
    void serverSideBusinessErrorHidesItsMessage() throws Exception
    {
        MvcResult result = mvc.perform(get("/test/errors/internal"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.detail").value(ProblemDetailsAdvice.INTERNAL_DETAIL))
                .andExpect(jsonPath("$.code").value("GF-COMMON-500"))
                .andReturn();

        assertThat(result.getResponse().getContentAsString()).doesNotContain("database password");
    }

    @Test
    void unexpectedExceptionIsGenericAndCorrelated() throws Exception
    {
        MvcResult result = mvc.perform(get("/test/errors/unexpected"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.detail").value(ProblemDetailsAdvice.INTERNAL_DETAIL))
                .andExpect(jsonPath("$.code").value("GF-COMMON-500"))
                .andReturn();

        String requestId = result.getResponse().getHeader(RequestIdFilter.HEADER);
        assertThat(requestId).isNotBlank();
        assertThat(result.getResponse().getContentAsString()).contains(requestId).doesNotContain("secret");
    }

    @Test
    void validationErrorsListTheFields() throws Exception
    {
        mvc.perform(post("/test/errors/validated").with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"name\":\" \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("GF-COMMON-400"))
                .andExpect(jsonPath("$.errors[0].field").value("name"))
                .andExpect(jsonPath("$.errors[0].message").isNotEmpty());
    }

    @Test
    void businessErrorsCanNameTheInputsAtFaultInTheRequestLanguage() throws Exception
    {
        mvc.perform(get("/test/errors/fields").header(HttpHeaders.ACCEPT_LANGUAGE, "zh-CN"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("GF-COMMON-400"))
                .andExpect(jsonPath("$.errors[0].field").value("url"))
                .andExpect(jsonPath("$.errors[0].message").value("必填。"))
                .andExpect(jsonPath("$.errors[1].field").value("mode"))
                .andExpect(jsonPath("$.errors[1].message").value("error.unknown.key"));
    }

    @Test
    void frameworkErrorsAreProblemDetailsToo() throws Exception
    {
        mvc.perform(delete("/test/errors/not-found").with(csrf()))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code").value("GF-COMMON-405"))
                .andExpect(jsonPath("$.requestId").isNotEmpty());
        mvc.perform(post("/test/errors/validated").with(csrf()).contentType(MediaType.TEXT_PLAIN).content("x"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("GF-COMMON-415"));
        mvc.perform(get("/api/v1/does-not-exist").with(user("tester")))
                .andExpect(status().isNotFound())
                .andExpect(header().exists(RequestIdFilter.HEADER))
                .andExpect(jsonPath("$.code").value("GF-COMMON-404"));
    }

    @Test
    void requestIdIsOmittedWhenTheRequestIsNotAServletRequest()
    {
        ResponseEntity<Object> response = new ProblemDetailsAdvice().handleExceptionInternal(
                new ErrorResponseException(HttpStatus.BAD_REQUEST), null, new HttpHeaders(), HttpStatus.BAD_REQUEST,
                mock(WebRequest.class));

        assertThat(response).isNotNull();
        assertThat(response.getBody()).isInstanceOfSatisfying(org.springframework.http.ProblemDetail.class, problem -> {
            assertThat(problem.getProperties()).containsEntry(ProblemDetailsAdvice.CODE, "GF-COMMON-400")
                    .doesNotContainKey(ProblemDetailsAdvice.REQUEST_ID);
        });
    }

    @Test
    void nativeRequestWithoutServletRequestHasNoRequestId()
    {
        NativeWebRequest request = mock(NativeWebRequest.class);
        when(request.getNativeRequest(HttpServletRequest.class)).thenReturn(null);

        ResponseEntity<Object> response = new ProblemDetailsAdvice().handleExceptionInternal(
                new ErrorResponseException(HttpStatus.CONFLICT), null, new HttpHeaders(), HttpStatus.CONFLICT, request);

        assertThat(response).isNotNull();
        assertThat(response.getBody()).isInstanceOfSatisfying(org.springframework.http.ProblemDetail.class,
                problem -> assertThat(problem.getProperties()).doesNotContainKey(ProblemDetailsAdvice.REQUEST_ID));
    }

    @Test
    void explicitNonProblemBodyIsLeftUntouched()
    {
        ResponseEntity<Object> response = new ProblemDetailsAdvice().handleExceptionInternal(
                new ErrorResponseException(HttpStatus.BAD_REQUEST), "plain", new HttpHeaders(), HttpStatus.BAD_REQUEST,
                mock(WebRequest.class));

        assertThat(response).isNotNull();
        assertThat(response.getBody()).isEqualTo("plain");
    }

    /** Endpoints that fail in every way the advice must handle; registered for this test only. */
    @RestController
    static class FailingController
    {
        @GetMapping("/test/errors/not-found")
        String notFound()
        {
            throw new GrantForgeException(CommonErrorCode.NOT_FOUND, "user 7 not found", "user", 7);
        }

        @GetMapping("/test/errors/internal")
        String internal()
        {
            throw new GrantForgeException(CommonErrorCode.INTERNAL, "database password rejected");
        }

        @GetMapping("/test/errors/unexpected")
        String unexpected()
        {
            throw new IllegalStateException("secret internal state");
        }

        @GetMapping("/test/errors/fields")
        String fields()
        {
            throw new GrantForgeException(CommonErrorCode.BAD_REQUEST, "bad settings").withFieldIssues(List.of(
                    FieldIssue.of("url", "error.service.config.required"), FieldIssue.of("mode", "error.unknown.key")));
        }

        @PostMapping("/test/errors/validated")
        String validated(@Valid @RequestBody NameBody body)
        {
            return body.name();
        }
    }

    /** Request body with a constraint. */
    record NameBody(@NotBlank String name)
    {
    }
}

// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.sdk;

import org.devlive.grantforge.sdk.jpa.TestOrder;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class DataEntityRegistrarTest
{
    @Test
    void declaresEntitiesWithATokenOfTheClientsOwn()
    {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://gf.example");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://gf.example/oauth2/token")).andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Basic Z2ZfYTpzMw=="))
                .andExpect(content().formDataContains(Map.of("grant_type", "client_credentials", "scope", "catalog")))
                .andRespond(withSuccess("{\"access_token\": \"m1\", \"token_type\": \"Bearer\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://gf.example/api/v1/open/catalog/data-entities")).andExpect(method(HttpMethod.PUT))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer m1"))
                .andExpect(jsonPath("$.entities[0].code").value("order"))
                .andExpect(jsonPath("$.entities[0].fields[1].choices[1]").value("PAID"))
                .andRespond(withSuccess("{\"declared\": 1}", MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://gf.example/oauth2/token")).andRespond(withStatus(HttpStatus.UNAUTHORIZED));
        server.expect(requestTo("https://gf.example/oauth2/token")).andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));
        DataEntityRegistrar registrar = new DataEntityRegistrar(builder.build(), "gf_a", "s3");

        assertThat(registrar.declare(List.of(TestOrder.class))).isTrue();
        assertThat(registrar.declare(List.of(TestOrder.class))).isFalse();
        assertThat(registrar.declare(List.of(TestOrder.class))).isFalse();
        server.verify();
        assertThat(DataEntityRegistrar.annotated(List.of(TestOrder.class, String.class))).containsExactly(TestOrder.class);
    }
}

// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.sdk;

import org.devlive.grantforge.sdk.GrantForgeException.Reason;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;

import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.headerDoesNotExist;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class GrantForgeClientTest
{
    private static final String URL = "https://gf.example/api/v1/open/me/authorization";

    private final AtomicReference<Instant> time = new AtomicReference<>(Instant.parse("2026-10-04T00:00:00Z"));
    private MockRestServiceServer server;
    private GrantForgeClient client;

    @BeforeEach
    void bind()
    {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://gf.example");
        server = MockRestServiceServer.bindTo(builder).build();
        client = new GrantForgeClient(builder.build(), Duration.ofSeconds(30), 2, clock());
    }

    @Test
    void keepsAnswersForTheTtlAndRevalidatesThemWithTheirEtag()
    {
        server.expect(requestTo(URL)).andExpect(method(HttpMethod.GET)).andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer t1"))
                .andExpect(headerDoesNotExist(HttpHeaders.IF_NONE_MATCH))
                .andRespond(withSuccess(SdkTestData.BODY, MediaType.APPLICATION_JSON).header(HttpHeaders.ETAG, "\"7\""));
        server.expect(requestTo(URL)).andExpect(header(HttpHeaders.IF_NONE_MATCH, "\"7\"")).andRespond(withStatus(HttpStatus.NOT_MODIFIED));
        server.expect(requestTo(URL)).andExpect(header(HttpHeaders.IF_NONE_MATCH, "\"7\""))
                .andRespond(withSuccess(SdkTestData.BODY.replace("\"orders.read\"", "\"orders.read\", \"orders.delete\"")
                        .replace("\"version\": 7", "\"version\": 8"), MediaType.APPLICATION_JSON));

        assertThat(client.authorization("t1")).isEqualTo(SdkTestData.ada());
        assertThat(client.authorization("t1").hasPermission("orders.read")).isTrue();
        time.set(requireNonNull(time.get()).plusSeconds(31));
        assertThat(client.authorization("t1").version()).isEqualTo(7);
        assertThat(client.authorization("t1").version()).isEqualTo(7);
        time.set(requireNonNull(time.get()).plusSeconds(31));
        UserAuthorization changed = client.authorization("t1");
        assertThat(changed.hasPermission("orders.delete")).isTrue();
        server.verify();
    }

    @Test
    void forgetsTokensGrantForgeRefusesButKeepsAnswersWhileItIsDown()
    {
        server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.UNAUTHORIZED));
        server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.FORBIDDEN));
        server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));
        server.expect(requestTo(URL)).andRespond(withException(new IOException("refused")));
        server.expect(requestTo(URL)).andRespond(withSuccess());
        server.expect(requestTo(URL)).andRespond(withSuccess(SdkTestData.BODY, MediaType.APPLICATION_JSON));
        server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.BAD_GATEWAY));
        server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.UNAUTHORIZED));
        server.expect(requestTo(URL)).andRespond(withSuccess(SdkTestData.BODY, MediaType.APPLICATION_JSON));

        assertThat(reasonOf("gone")).isEqualTo(Reason.UNAUTHENTICATED);
        assertThat(reasonOf("machine")).isEqualTo(Reason.FORBIDDEN);
        assertThat(reasonOf("t1")).isEqualTo(Reason.UNAVAILABLE);
        assertThatThrownBy(() -> client.authorization("t1")).isInstanceOfSatisfying(GrantForgeException.class,
                refused -> assertThat(refused).hasCauseInstanceOf(Exception.class));
        assertThat(reasonOf("t1")).isEqualTo(Reason.UNAVAILABLE);
        client.authorization("t2");
        time.set(requireNonNull(time.get()).plusSeconds(31));
        // GrantForge is down: the request fails, but the answer stays for the next try.
        assertThat(reasonOf("t2")).isEqualTo(Reason.UNAVAILABLE);
        // A refusal forgets it: the next call fetches anew, without an ETag.
        assertThat(reasonOf("t2")).isEqualTo(Reason.UNAUTHENTICATED);
        assertThat(client.authorization("t2").username()).isEqualTo("ada");
        server.verify();
    }

    private Reason reasonOf(String token)
    {
        try {
            client.authorization(token);
            throw new AssertionError("expected a refusal");
        }
        catch (GrantForgeException refused) {
            return refused.getReason();
        }
    }

    @Test
    void keepsOnlyTheMostRecentUsersAndForgetsOnRequest()
    {
        server.expect(ExpectedCount.times(5), requestTo(URL)).andRespond(withSuccess(SdkTestData.BODY, MediaType.APPLICATION_JSON));

        client.authorization("a");
        client.authorization("b");
        client.authorization("a");
        client.authorization("c");
        // "b" was the least recently used of two, so it went; "a" stays.
        client.authorization("a");
        client.authorization("b");
        client.forget("b");
        client.authorization("b");
        server.verify();
    }

    @Test
    void keepsDataAccessApartFromPermissions()
    {
        String access = "{\"subject\": {\"accountId\": \"42\", \"tenantId\": \"3\", \"username\": \"ada\", \"orgUnitIds\": [\"7\"],"
                + " \"orgUnitsAndBelow\": [\"7\", \"8\"], \"groupCodes\": [], \"positionCodes\": []},"
                + " \"entities\": [{\"entity\": \"order\", \"action\": \"READ\", \"allow\": [{\"scope\": \"SELF\", \"condition\": null,"
                + " \"orgUnitIds\": []}], \"deny\": []}], \"version\": 5}";
        server.expect(ExpectedCount.twice(), requestTo("https://gf.example/api/v1/open/me/data-access"))
                .andRespond(withSuccess(access, MediaType.APPLICATION_JSON));
        server.expect(ExpectedCount.once(), requestTo(URL)).andRespond(withSuccess(SdkTestData.BODY, MediaType.APPLICATION_JSON));

        UserDataAccess first = client.dataAccess("t1");
        assertThat(first.subject().orgUnitsAndBelow()).containsExactly("7", "8");
        assertThat(first.rules("order", DataAction.READ)).isPresent();
        assertThat(client.dataAccess("t1")).isEqualTo(first);
        assertThat(client.authorization("t1").username()).isEqualTo("ada");
        client.forget("t1");
        assertThat(client.dataAccess("t1").version()).isEqualTo(5);
        server.verify();
    }

    private Clock clock()
    {
        return new Clock()
        {
            @Override
            public ZoneId getZone()
            {
                return ZoneOffset.UTC;
            }

            @Override
            public Clock withZone(ZoneId zone)
            {
                return this;
            }

            @Override
            public Instant instant()
            {
                return requireNonNull(time.get());
            }
        };
    }
}

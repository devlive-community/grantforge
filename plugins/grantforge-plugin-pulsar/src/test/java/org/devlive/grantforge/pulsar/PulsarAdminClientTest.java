// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.pulsar;

import org.apache.pulsar.client.admin.PulsarAdmin;
import org.apache.pulsar.client.admin.PulsarAdminException;
import org.apache.pulsar.client.api.Authentication;
import org.apache.pulsar.client.api.PulsarClientException;
import org.devlive.grantforge.plugin.api.LookupException;
import org.junit.jupiter.api.Test;

import java.net.ConnectException;
import java.net.UnknownHostException;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class PulsarAdminClientTest
{
    private static PulsarSettings settings(String... values)
    {
        return PulsarSettings.of(PulsarSettingsTest.config(values));
    }

    private static LookupException.Reason reason(Throwable failed)
    {
        return PulsarAdminClient.failure(failed).getReason();
    }

    @Test
    void runsWorkAndClosesTheClient() throws Exception
    {
        PulsarAdmin admin = mock(PulsarAdmin.class);
        PulsarAdminClient client = new PulsarAdminClient(settings(), ignored -> admin);
        String done = client.run(open -> open == admin ? "done" : "other");
        assertThat(done).isEqualTo("done");
        verify(admin).close();
    }

    @Test
    void namesWhyWorkFails()
    {
        PulsarAdmin admin = mock(PulsarAdmin.class);
        PulsarAdminClient client = new PulsarAdminClient(settings(), ignored -> admin);
        assertThatThrownBy(() -> client.run(open -> {
            throw new PulsarAdminException(new ConnectException("Connection refused"), "Connection refused", 0);
        })).isInstanceOfSatisfying(LookupException.class, failed -> assertThat(failed.getReason()).isEqualTo(LookupException.Reason.UNREACHABLE));
        PulsarAdminClient broken = new PulsarAdminClient(settings(), ignored -> {
            throw new PulsarClientException("cannot read /etc/client.pem");
        });
        assertThatThrownBy(() -> broken.run(open -> "never")).isInstanceOfSatisfying(LookupException.class,
                failed -> assertThat(failed).hasMessage("cannot read /etc/client.pem"));
    }

    @Test
    void tellsARefusedSignInFromARefusedRequest()
    {
        assertThat(reason(new PulsarAdminException(null, "HTTP 401 Authentication required", 401))).isEqualTo(LookupException.Reason.AUTHENTICATION_FAILED);
        assertThat(reason(new PulsarAdminException(null, "Need to authenticate to perform the request", 403)))
                .isEqualTo(LookupException.Reason.AUTHENTICATION_FAILED);
        assertThat(reason(new PulsarAdminException(null, "Client is not authorized to list tenants", 401))).isEqualTo(LookupException.Reason.ACCESS_DENIED);
        assertThat(reason(new PulsarAdminException(null, "Forbidden", 403))).isEqualTo(LookupException.Reason.ACCESS_DENIED);
        assertThat(reason(new PulsarAdminException(null, "Tenant does not exist", 404))).isEqualTo(LookupException.Reason.NOT_FOUND);
        assertThat(reason(new PulsarAdminException(null, "Internal error", 500))).isEqualTo(LookupException.Reason.FAILED);
        assertThat(reason(new PulsarAdminException(null, "HTTP 502 Bad Gateway", 502))).isEqualTo(LookupException.Reason.UNREACHABLE);
        assertThat(reason(new PulsarAdminException(null, "HTTP 504 Gateway Timeout", 504))).isEqualTo(LookupException.Reason.UNREACHABLE);
        assertThat(reason(new IllegalStateException("wrapped", new PulsarAdminException(null, "Forbidden", 403))))
                .isEqualTo(LookupException.Reason.ACCESS_DENIED);
    }

    @Test
    void namesTheClientsOwnFailures()
    {
        assertThat(reason(new PulsarClientException.AuthenticationException("bad credentials"))).isEqualTo(LookupException.Reason.AUTHENTICATION_FAILED);
        assertThat(reason(new PulsarClientException.AuthorizationException("not allowed"))).isEqualTo(LookupException.Reason.ACCESS_DENIED);
        assertThat(reason(new RuntimeException(new UnknownHostException("nowhere")))).isEqualTo(LookupException.Reason.UNREACHABLE);
        // What the admin client throws for a broker that refuses connections, once its retries are exhausted.
        assertThat(reason(new PulsarAdminException(new IllegalStateException("Number of retries has been exhausted",
                new ConnectException("Connection refused: /127.0.0.1:1")), "retries exhausted", 500))).isEqualTo(LookupException.Reason.UNREACHABLE);
        assertThat(reason(new RuntimeException(new TimeoutException()))).isEqualTo(LookupException.Reason.UNREACHABLE);
        assertThat(reason(new PulsarAdminException.TimeoutException(new TimeoutException("slow")))).isEqualTo(LookupException.Reason.UNREACHABLE);
        assertThat(reason(new IllegalStateException("odd"))).isEqualTo(LookupException.Reason.FAILED);
        LookupException known = new LookupException(LookupException.Reason.NOT_FOUND, "gone");
        assertThat(PulsarAdminClient.failure(known)).isSameAs(known);
    }

    @Test
    void namesAFailureWithoutAMessageByItsCause()
    {
        assertThat(PulsarAdminClient.failure(new RuntimeException(null, new ConnectException("Connection refused")))).hasMessage("Connection refused");
        assertThat(PulsarAdminClient.failure(new IllegalStateException())).hasMessage("IllegalStateException");
    }

    private static String method(String... values) throws PulsarClientException
    {
        Authentication authentication = PulsarAdminClient.authentication(settings(values));
        return authentication == null ? "none" : authentication.getAuthMethodName();
    }

    @Test
    void signsInAsTheSettingsSay() throws Exception
    {
        assertThat(method()).isEqualTo("none");
        assertThat(method(PulsarSettings.AUTHENTICATION, "token", PulsarSettings.TOKEN_FIELD, "t")).isEqualTo("token");
        assertThat(method(PulsarSettings.AUTHENTICATION, "basic", PulsarSettings.USER, "u", PulsarSettings.PASSWORD, "p")).isEqualTo("basic");
        assertThat(method(PulsarSettings.AUTHENTICATION, "tls", PulsarSettings.CERTIFICATE, "/c.pem", PulsarSettings.KEY, "/k.pem")).isEqualTo("tls");
        assertThat(method(PulsarSettings.AUTHENTICATION, "oauth2", PulsarSettings.ISSUER_URL, "https://auth.example.com/", PulsarSettings.PRIVATE_KEY,
                "/etc/credentials.json", PulsarSettings.AUDIENCE, "urn:pulsar", PulsarSettings.SCOPE, "admin"))
                .isEqualTo("token");
        assertThat(method(PulsarSettings.AUTHENTICATION, "oauth2", PulsarSettings.ISSUER_URL, "https://auth.example.com/", PulsarSettings.PRIVATE_KEY,
                "/etc/credentials.json")).isEqualTo("token");
    }

    @Test
    void opensPulsarsOwnClient() throws Exception
    {
        try (PulsarAdmin admin = PulsarAdminClient.open(settings(PulsarSettings.AUTHENTICATION, "token", PulsarSettings.TOKEN_FIELD, "t",
                PulsarSettings.TRUST_CERTS, "/etc/ca.pem"))) {
            assertThat(admin.getServiceUrl()).isEqualTo("http://broker1.example.com:8080");
        }
        try (PulsarAdmin admin = PulsarAdminClient.open(settings())) {
            assertThat(admin.getServiceUrl()).isEqualTo("http://broker1.example.com:8080");
        }
    }
}

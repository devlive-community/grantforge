// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.kafka;

import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.common.KafkaException;
import org.apache.kafka.common.errors.SaslAuthenticationException;
import org.apache.kafka.common.errors.TimeoutException;
import org.apache.kafka.common.errors.TopicAuthorizationException;
import org.devlive.grantforge.plugin.api.LookupException;
import org.junit.jupiter.api.Test;

import javax.net.ssl.SSLHandshakeException;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicReference;

import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class KafkaClientTest
{
    private final Admin admin = mock(Admin.class);
    private final AtomicReference<Map<String, Object>> opened = new AtomicReference<>();
    private final KafkaClient client = new KafkaClient(KafkaSettings.of(KafkaSettingsTest.config()), config -> {
        opened.set(config);
        return admin;
    });

    private static LookupException.Reason reasonOf(Throwable failure)
    {
        return KafkaClient.failure(failure).getReason();
    }

    @Test
    void runsWithThePluginsClassLoaderAndClosesTheClient()
    {
        ClassLoader before = Thread.currentThread().getContextClassLoader();
        ClassLoader during = client.run(open -> Thread.currentThread().getContextClassLoader());
        assertThat(during).isSameAs(KafkaClient.class.getClassLoader());
        assertThat(Thread.currentThread().getContextClassLoader()).isSameAs(before);
        Map<String, Object> config = requireNonNull(opened.get());
        assertThat(config).containsEntry("bootstrap.servers", "kafka1:9092,kafka2:9093");
        assertThat(String.valueOf(config.get("client.id"))).startsWith("grantforge-lookup-");
        verify(admin).close(any(Duration.class));
    }

    @Test
    void namesWhyKafkaRefused()
    {
        assertThatThrownBy(() -> client.run(open -> {
            throw new ExecutionException(new TopicAuthorizationException("not allowed"));
        })).isInstanceOfSatisfying(LookupException.class, failed -> {
            assertThat(failed.getReason()).isEqualTo(LookupException.Reason.ACCESS_DENIED);
            assertThat(failed.getMessage()).isEqualTo("not allowed");
        });
        assertThatThrownBy(() -> client.run(open -> {
            throw new ExecutionException(null);
        })).isInstanceOfSatisfying(LookupException.class, failed -> assertThat(failed.getReason()).isEqualTo(LookupException.Reason.FAILED));
        assertThatThrownBy(() -> client.run(open -> {
            throw new InterruptedException("stop");
        })).isInstanceOf(LookupException.class).hasMessageContaining("interrupted");
        assertThat(Thread.interrupted()).isTrue();
        KafkaClient broken = new KafkaClient(KafkaSettings.of(KafkaSettingsTest.config()), config -> {
            throw new KafkaException("Failed to create new KafkaAdminClient", new SaslAuthenticationException("bad password"));
        });
        assertThatThrownBy(() -> broken.run(open -> "never")).isInstanceOfSatisfying(LookupException.class,
                failed -> assertThat(failed.getReason()).isEqualTo(LookupException.Reason.AUTHENTICATION_FAILED));
    }

    @Test
    void mapsEachKindOfFailure()
    {
        assertThat(reasonOf(new SaslAuthenticationException("x"))).isEqualTo(LookupException.Reason.AUTHENTICATION_FAILED);
        assertThat(reasonOf(new TopicAuthorizationException("x"))).isEqualTo(LookupException.Reason.ACCESS_DENIED);
        assertThat(reasonOf(new TimeoutException("x"))).isEqualTo(LookupException.Reason.UNREACHABLE);
        assertThat(reasonOf(new KafkaException("wrapped", new SSLHandshakeException("no trust")))).isEqualTo(LookupException.Reason.UNREACHABLE);
        assertThat(reasonOf(new IllegalStateException("odd"))).isEqualTo(LookupException.Reason.FAILED);
        LookupException own = new LookupException(LookupException.Reason.NOT_FOUND, "gone");
        assertThat(KafkaClient.failure(own)).isSameAs(own);
        assertThat(KafkaClient.message(new IllegalStateException("first\nsecond"))).isEqualTo("first");
        assertThat(KafkaClient.message(new IllegalStateException())).isEqualTo("IllegalStateException");
    }
}

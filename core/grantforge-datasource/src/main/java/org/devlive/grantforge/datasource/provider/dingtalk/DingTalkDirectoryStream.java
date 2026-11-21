// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.datasource.provider.dingtalk;

import com.dingtalk.open.app.api.OpenDingTalkClient;
import com.dingtalk.open.app.api.OpenDingTalkStreamClientBuilder;
import com.dingtalk.open.app.api.message.GenericOpenDingTalkEvent;
import com.dingtalk.open.app.api.security.AuthClientCredential;
import com.dingtalk.open.app.stream.protocol.event.EventAckStatus;
import jakarta.annotation.PreDestroy;
import org.devlive.grantforge.datasource.application.DataSourceService;
import org.devlive.grantforge.identity.application.SecretBox;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;

/** Runs one official Stream SDK connection per enabled DingTalk application on this instance. */
@Component
@ConditionalOnProperty(name = "grantforge.dingtalk.directory-stream.enabled", matchIfMissing = true)
final class DingTalkDirectoryStream
{
    private static final Logger LOG = LoggerFactory.getLogger(DingTalkDirectoryStream.class);
    private static final Set<String> DIRECTORY_EVENTS = Set.of("user_add_org", "user_modify_org", "user_leave_org");
    private static final String DEPARTURE = "user_leave_org";
    private final DataSourceService sources;
    private final SecretBox secrets;
    private final Map<String, Connection> connections = new HashMap<>();
    private final ReentrantLock lifecycle = new ReentrantLock();
    private boolean closed;

    DingTalkDirectoryStream(DataSourceService sources, SecretBox secrets)
    {
        this.sources = sources;
        this.secrets = secrets;
    }

    @Bean(defaultCandidate = false)
    ThreadPoolTaskScheduler dingTalkStreamScheduler()
    {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setThreadNamePrefix("dingtalk-stream-");
        return scheduler;
    }

    @Scheduled(scheduler = "dingTalkStreamScheduler", initialDelayString = "PT2S", fixedDelayString = "PT15S")
    void refresh()
    {
        Map<String, String> desired = sources.dingTalkStreamCredentials();
        lifecycle.lock();
        try {
            if (closed) {
                return;
            }
            connections.entrySet().removeIf(entry -> {
                if (entry.getValue().credential().equals(desired.get(entry.getKey()))) {
                    return false;
                }
                stop(entry.getValue().client());
                return true;
            });
            desired.forEach((appKey, credential) -> connections.computeIfAbsent(appKey, key -> connect(key, credential)));
        }
        finally {
            lifecycle.unlock();
        }
    }

    @PreDestroy
    void shutdown()
    {
        lifecycle.lock();
        try {
            closed = true;
            connections.values().forEach(connection -> stop(connection.client()));
            connections.clear();
        }
        finally {
            lifecycle.unlock();
        }
    }

    private @Nullable Connection connect(String appKey, String credential)
    {
        OpenDingTalkClient client = null;
        try {
            client = OpenDingTalkStreamClientBuilder.custom()
                    .credential(new AuthClientCredential(appKey, secrets.open(credential)))
                    .registerAllEventListener(event -> receive(appKey, event))
                    .build();
            client.start();
            return new Connection(credential, client);
        }
        catch (Exception failure) {
            if (client != null) {
                stop(client);
            }
            // Do not log SDK exception text, which may include connection tickets.
            LOG.warn("Could not start DingTalk directory Stream client; will retry");
            return null;
        }
    }

    private static void stop(OpenDingTalkClient client)
    {
        try {
            client.stop();
        }
        catch (Exception failure) {
            LOG.warn("Could not stop DingTalk directory Stream client");
        }
    }

    EventAckStatus receive(String appKey, GenericOpenDingTalkEvent event)
    {
        String eventType = event.getEventType();
        if (eventType == null || !DIRECTORY_EVENTS.contains(eventType)) {
            return EventAckStatus.SUCCESS;
        }
        String corpId = event.getEventCorpId();
        var data = event.getData();
        Object values = data == null ? null : data.get("userId");
        if (values == null && data != null) {
            values = data.get("UserId");
        }
        if (values == null && data != null) {
            values = data.get("userid");
        }
        Collection<?> ids = values instanceof String id ? Set.of(id)
                : values instanceof Collection<?> collection ? collection : Set.of();
        if (corpId == null || corpId.isBlank() || ids.isEmpty()) {
            LOG.warn("Ignoring DingTalk directory event with missing enterprise or employee identifiers");
            return EventAckStatus.SUCCESS;
        }
        Set<String> employees = new HashSet<>();
        for (Object value : ids) {
            if (!(value instanceof String id) || id.isBlank() || id.length() > 255) {
                LOG.warn("Ignoring DingTalk directory event with invalid employee identifiers");
                return EventAckStatus.SUCCESS;
            }
            employees.add(id);
        }
        try {
            if (DEPARTURE.equals(eventType)) {
                return sources.dingTalkDeparted(appKey, corpId, employees) ? EventAckStatus.SUCCESS : EventAckStatus.LATER;
            }
            sources.dingTalkUsersChanged(appKey, corpId, employees);
            return EventAckStatus.SUCCESS;
        }
        catch (RuntimeException failure) {
            LOG.warn("DingTalk directory event could not be applied; requesting event redelivery");
            return EventAckStatus.LATER;
        }
    }

    private record Connection(String credential, OpenDingTalkClient client)
    {
    }
}

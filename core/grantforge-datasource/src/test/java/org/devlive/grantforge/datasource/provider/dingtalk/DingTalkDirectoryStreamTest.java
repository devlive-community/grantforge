// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.datasource.provider.dingtalk;

import com.dingtalk.open.app.api.message.GenericOpenDingTalkEvent;
import com.dingtalk.open.app.stream.protocol.event.EventAckStatus;
import org.devlive.grantforge.datasource.application.DataSourceService;
import org.devlive.grantforge.identity.application.SecretBox;
import org.junit.jupiter.api.Test;
import shade.com.alibaba.fastjson2.JSONObject;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class DingTalkDirectoryStreamTest
{
    private final DataSourceService sources = mock(DataSourceService.class);
    private final DingTalkDirectoryStream stream = new DingTalkDirectoryStream(sources, mock(SecretBox.class));

    @Test
    void acknowledgesOnlyAfterTheMatchingEventWasHandled()
    {
        var event = event("user_leave_org", List.of("employee", "employee"));
        when(sources.dingTalkDeparted("app", "corp", Set.of("employee"))).thenReturn(false, true)
                .thenThrow(new IllegalStateException("database unavailable"));
        assertThat(stream.receive("app", event)).isEqualTo(EventAckStatus.LATER);
        assertThat(stream.receive("app", event)).isEqualTo(EventAckStatus.SUCCESS);
        assertThat(stream.receive("app", event)).isEqualTo(EventAckStatus.LATER);
    }

    @Test
    void ignoresOtherEventsAndMalformedDepartureIdentifiers()
    {
        assertThat(stream.receive("app", event("user_role_change", List.of("employee")))).isEqualTo(EventAckStatus.SUCCESS);
        assertThat(stream.receive("app", event("user_leave_org", List.of(42)))).isEqualTo(EventAckStatus.SUCCESS);
        verifyNoInteractions(sources);
    }

    @Test
    void reconcilesAddedAndChangedEmployees()
    {
        assertThat(stream.receive("app", event("user_add_org", List.of("new-employee")))).isEqualTo(EventAckStatus.SUCCESS);
        var change = event("user_modify_org", List.of());
        change.setData(JSONObject.of("userid", "changed-employee"));
        assertThat(stream.receive("app", change)).isEqualTo(EventAckStatus.SUCCESS);

        verify(sources).dingTalkUsersChanged("app", "corp", Set.of("new-employee"));
        verify(sources).dingTalkUsersChanged("app", "corp", Set.of("changed-employee"));
    }

    private static GenericOpenDingTalkEvent event(String type, List<?> ids)
    {
        var event = new GenericOpenDingTalkEvent();
        event.setEventType(type);
        event.setEventCorpId("corp");
        event.setData(JSONObject.of("userId", ids));
        return event;
    }
}

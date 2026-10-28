// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.datasource.provider.dingtalk;

import com.dingtalk.api.DingTalkClient;
import com.dingtalk.api.request.OapiAuthScopesRequest;
import com.dingtalk.api.request.OapiGettokenRequest;
import com.dingtalk.api.request.OapiV2DepartmentGetRequest;
import com.dingtalk.api.request.OapiV2DepartmentListsubRequest;
import com.dingtalk.api.request.OapiV2UserListRequest;
import com.dingtalk.api.response.OapiAuthScopesResponse;
import com.dingtalk.api.response.OapiGettokenResponse;
import com.dingtalk.api.response.OapiV2DepartmentGetResponse;
import com.dingtalk.api.response.OapiV2DepartmentListsubResponse;
import com.dingtalk.api.response.OapiV2UserListResponse;
import org.devlive.grantforge.common.error.GrantForgeException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DingTalkDirectoryTest
{
    private DingTalkDirectory directory;
    private DingTalkClient client;

    @BeforeEach
    void sdk() throws Exception
    {
        client = mock(DingTalkClient.class);
        directory = spy(new DingTalkDirectory());
        doReturn(client).when(directory).client(anyString());
        OapiGettokenResponse token = new OapiGettokenResponse();
        token.setErrcode(0L);
        token.setAccessToken("private-token");
        when(client.execute(any(OapiGettokenRequest.class), isNull())).thenReturn(token);
        OapiAuthScopesResponse scope = new OapiAuthScopesResponse();
        scope.setErrcode(0L);
        var range = new OapiAuthScopesResponse.AuthOrgScopes();
        range.setAuthedDept(List.of(1L));
        range.setAuthedUser(List.of());
        scope.setAuthOrgScopes(range);
        when(client.execute(any(OapiAuthScopesRequest.class), anyString())).thenReturn(scope);
        OapiV2DepartmentGetResponse root = new OapiV2DepartmentGetResponse();
        root.setErrcode(0L);
        var rootData = new OapiV2DepartmentGetResponse.DeptGetResponse();
        rootData.setDeptId(1L);
        rootData.setName("Acme");
        root.setResult(rootData);
        when(client.execute(any(OapiV2DepartmentGetRequest.class), anyString())).thenReturn(root);
        OapiV2DepartmentListsubResponse children = new OapiV2DepartmentListsubResponse();
        children.setErrcode(0L);
        children.setResult(List.of());
        when(client.execute(any(OapiV2DepartmentListsubRequest.class), anyString())).thenReturn(children);
    }

    @Test
    void officialClientLoadsWithTheApplicationDependencies()
    {
        assertThat(new DingTalkDirectory().client("/gettoken")).isInstanceOf(com.dingtalk.api.DefaultDingTalkClient.class);
    }

    @Test
    void usesSdkCursorAndMergesEmployeesFromEveryDepartment() throws Exception
    {
        OapiV2DepartmentListsubResponse first = new OapiV2DepartmentListsubResponse();
        first.setErrcode(0L);
        var child = new OapiV2DepartmentListsubResponse.DeptBaseResponse();
        child.setDeptId(10L);
        child.setParentId(1L);
        child.setName("Engineering");
        first.setResult(List.of(child));
        OapiV2DepartmentListsubResponse empty = new OapiV2DepartmentListsubResponse();
        empty.setErrcode(0L);
        empty.setResult(List.of());
        when(client.execute(any(OapiV2DepartmentListsubRequest.class), anyString())).thenReturn(first, empty);
        var employee = new OapiV2UserListResponse.ListUserResponse();
        employee.setUserid("u1");
        employee.setEmail("ALICE@example.com");
        when(client.execute(any(OapiV2UserListRequest.class), anyString())).thenReturn(
            page(List.of(employee), true, 123L), page(List.of(), false, 0L), page(List.of(employee), false, 0L));
        var snapshot = directory.list(new DingTalkSettings("corp", "app", 1), "secret");
        assertThat(snapshot.departments()).extracting(DingTalkDirectory.Department::id).containsExactly("1", "10");
        assertThat(snapshot.employees()).hasSize(1);
        assertThat(snapshot.employees().get(0).departments()).containsExactly("1", "10");
        assertThat(snapshot.employees().get(0).email()).isEqualTo("alice@example.com");
        ArgumentCaptor<OapiV2UserListRequest> requests = ArgumentCaptor.forClass(OapiV2UserListRequest.class);
        verify(client, times(3)).execute(requests.capture(), eq("private-token"));
        assertThat(requests.getAllValues()).extracting(OapiV2UserListRequest::getCursor).containsExactly(0L, 123L, 0L);
    }

    @Test
    void rejectsRepeatedCursorsInsteadOfReturningPartialData() throws Exception
    {
        var employee = new OapiV2UserListResponse.ListUserResponse();
        employee.setUserid("u1");
        when(client.execute(any(OapiV2UserListRequest.class), anyString())).thenReturn(page(List.of(employee), true, 0L));
        assertThatThrownBy(() -> directory.list(new DingTalkSettings("corp", "app", 1), "secret"))
            .isInstanceOf(GrantForgeException.class).hasMessageContaining("repeated employee cursor");
    }

    @Test
    void rejectsMissingBusinessStatusAndNeverIncludesCredentialsInErrors() throws Exception
    {
        OapiGettokenResponse failed = new OapiGettokenResponse();
        failed.setErrmsg("sensitive secret and private-token");
        when(client.execute(any(OapiGettokenRequest.class), isNull())).thenReturn(failed);
        assertThatThrownBy(() -> directory.list(new DingTalkSettings("corp", "app", 1), "secret"))
            .isInstanceOf(GrantForgeException.class).hasMessageNotContaining("private-token").hasMessageNotContaining("secret");
    }

    @Test
    void allowsAnAbsentUserScopeWhenDepartmentScopeIsPresent() throws Exception
    {
        var response = new OapiAuthScopesResponse();
        response.setErrcode(0L);
        var range = new OapiAuthScopesResponse.AuthOrgScopes();
        range.setAuthedDept(List.of(1L));
        response.setAuthOrgScopes(range);
        when(client.execute(any(OapiAuthScopesRequest.class), anyString())).thenReturn(response);
        when(client.execute(any(OapiV2UserListRequest.class), anyString())).thenReturn(page(List.of(), false, 0L));
        assertThat(directory.list(new DingTalkSettings("corp", "app", 1), "secret").employees()).isEmpty();
    }

    @Test
    void retriesRateLimitsThroughTheSdk() throws Exception
    {
        OapiV2UserListResponse limited = new OapiV2UserListResponse();
        limited.setErrcode(88L);
        when(client.execute(any(OapiV2UserListRequest.class), anyString())).thenReturn(limited, page(List.of(), false, 0L));
        assertThat(directory.list(new DingTalkSettings("corp", "app", 1), "secret").employees()).isEmpty();
        verify(client, times(2)).execute(any(OapiV2UserListRequest.class), anyString());
    }

    private static OapiV2UserListResponse page(List<OapiV2UserListResponse.ListUserResponse> users, boolean more, long cursor)
    {
        var response = new OapiV2UserListResponse();
        response.setErrcode(0L);
        var result = new OapiV2UserListResponse.PageResult();
        result.setList(users);
        result.setHasMore(more);
        result.setNextCursor(cursor);
        response.setResult(result);
        return response;
    }
}

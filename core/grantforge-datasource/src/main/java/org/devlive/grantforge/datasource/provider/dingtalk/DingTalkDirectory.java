// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.datasource.provider.dingtalk;

import com.dingtalk.api.DefaultDingTalkClient;
import com.dingtalk.api.DingTalkClient;
import com.dingtalk.api.request.OapiAuthScopesRequest;
import com.dingtalk.api.request.OapiGettokenRequest;
import com.dingtalk.api.request.OapiV2DepartmentGetRequest;
import com.dingtalk.api.request.OapiV2DepartmentListsubRequest;
import com.dingtalk.api.request.OapiV2UserListRequest;
import com.dingtalk.api.response.OapiV2UserListResponse;
import com.taobao.api.ApiException;
import com.taobao.api.TaobaoRequest;
import com.taobao.api.TaobaoResponse;
import com.taobao.api.internal.util.TaobaoLogger;
import com.taobao.api.internal.util.WebV2Utils;
import org.devlive.grantforge.common.error.GrantForgeException;
import org.devlive.grantforge.datasource.application.DataSourceErrors;
import org.devlive.grantforge.datasource.domain.DirectoryEmployee;
import org.devlive.grantforge.datasource.provider.DirectoryProvider;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

/**
 * Reads a complete, bounded directory snapshot using the official /topapi SDK. Never exposes SDK errors or tokens.
 */
@Component
public class DingTalkDirectory implements DirectoryProvider
{
    @Override
    public String type()
    {
        return "DINGTALK";
    }

    @Override
    public Map<String, String> validate(Map<String, String> settings)
    {
        DingTalkSettings config = DingTalkSettings.from(settings);
        return Map.of("corpId", config.corpId(), "appKey", config.appKey(), "rootDepartmentId", Long.toString(config.rootDepartmentId()));
    }

    @Override
    public Snapshot fetch(Map<String, String> settings, String secret)
    {
        return list(DingTalkSettings.from(settings), secret);
    }

    /**
     * Fetches all pages before returning anything to the writer. Tokens live only for this fetch.
     *
     * @param settings the directory settings
     * @param secret   the credential; never logged
     * @return the result
     */
    @SuppressWarnings({"PMD.AvoidInstantiatingObjectsInLoops", "PMD.WhileLoopWithLiteralBoolean"}) // Each SDK page and department needs a fresh request.
    public Snapshot list(DingTalkSettings settings, String secret)
    {
        long deadline = System.nanoTime() + java.time.Duration.ofMinutes(10).toNanos();
        OapiGettokenRequest auth = new OapiGettokenRequest();
        auth.setAppkey(settings.appKey());
        auth.setAppsecret(secret);
        auth.setHttpMethod("GET");
        var tokenResponse = call("/gettoken", auth, null, response -> response.getErrcode(), deadline);
        String token = tokenResponse.getAccessToken();
        if (token == null || token.isBlank()) {
            throw unavailable("missing access token");
        }
        String scope = scope(token, deadline);
        OapiV2DepartmentGetRequest rootRequest = new OapiV2DepartmentGetRequest();
        rootRequest.setDeptId(settings.rootDepartmentId());
        var root = call("/topapi/v2/department/get", rootRequest, token, response -> response.getErrcode(), deadline).getResult();
        if (root == null || root.getName() == null || !Objects.equals(root.getDeptId(), settings.rootDepartmentId())) {
            throw unavailable("missing root department");
        }
        List<Department> departments = new ArrayList<>();
        departments.add(new Department(Long.toString(settings.rootDepartmentId()), null, root.getName(), 0));
        Set<Long> seen = new HashSet<>();
        seen.add(settings.rootDepartmentId());
        Deque<Long> pending = new ArrayDeque<>();
        pending.add(settings.rootDepartmentId());
        Map<String, Employee> users = new LinkedHashMap<>();
        while (!pending.isEmpty()) {
            long department = pending.removeFirst();
            OapiV2DepartmentListsubRequest children = new OapiV2DepartmentListsubRequest();
            children.setDeptId(department);
            var childrenResponse = call("/topapi/v2/department/listsub", children, token, response -> response.getErrcode(), deadline);
            if (childrenResponse.getResult() == null) {
                throw unavailable("missing department list");
            }
            int order = 0;
            for (var child : childrenResponse.getResult()) {
                Long id = child.getDeptId();
                if (id == null || id < 1 || child.getName() == null || !Objects.equals(child.getParentId(), department) || !seen.add(id)) {
                    throw unavailable("invalid department hierarchy");
                }
                departments.add(new Department(Long.toString(id), Long.toString(department), child.getName(), order));
                order++;
                pending.add(id);
            }
            long cursor = 0;
            Set<Long> cursors = new HashSet<>();
            do {
                if (!cursors.add(cursor)) {
                    throw unavailable("repeated employee cursor");
                }
                OapiV2UserListRequest page = new OapiV2UserListRequest();
                page.setDeptId(department);
                page.setCursor(cursor);
                page.setSize(100L);
                page.setContainAccessLimit(false);
                var result = call("/topapi/v2/user/list", page, token, response -> response.getErrcode(), deadline).getResult();
                if (result == null || result.getList() == null || result.getHasMore() == null) {
                    throw unavailable("incomplete employee page");
                }
                for (OapiV2UserListResponse.ListUserResponse user : result.getList()) {
                    String id = user.getUserid();
                    String address = DirectoryEmployee.normalizeEmail(user.getEmail());
                    if (address == null) {
                        address = DirectoryEmployee.normalizeEmail(user.getOrgEmail());
                    }
                    if (id == null || id.isBlank() || id.length() > 255) {
                        throw unavailable("invalid employee identifier");
                    }
                    Employee previous = users.get(id);
                    if (previous != null && !Objects.equals(previous.email(), address)) {
                        throw unavailable("employee changed during fetch; retry");
                    }
                    List<String> memberships = new ArrayList<>(previous == null ? List.of() : previous.departments());
                    memberships.add(Long.toString(department));
                    users.put(id, new Employee(id, address, List.copyOf(memberships)));
                }
                if (!result.getHasMore()) {
                    break;
                }
                Long next = result.getNextCursor();
                if (next == null || next < 0 || result.getList().isEmpty()) {
                    throw unavailable("missing employee cursor");
                }
                cursor = next;
            }
            while (true);
        }
        if (!scope.equals(scope(token, deadline))) {
            throw unavailable("directory permissions changed during fetch");
        }
        return new Snapshot(List.copyOf(departments), List.copyOf(users.values()), scope);
    }

    private String scope(String token, long deadline)
    {
        var response = call("/auth/scopes", new OapiAuthScopesRequest(), token, item -> item.getErrcode(), deadline);
        var scopes = response.getAuthOrgScopes();
        if (scopes == null || (scopes.getAuthedDept() == null && scopes.getAuthedUser() == null)) {
            throw unavailable("missing directory authorization scope");
        }
        List<Long> departmentScope = scopes.getAuthedDept() == null ? List.of() : scopes.getAuthedDept();
        List<String> userScope = scopes.getAuthedUser() == null ? List.of() : scopes.getAuthedUser();
        String value = departmentScope.stream().sorted().toList() + ":" + userScope.stream().sorted().toList();
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        }
        catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    /**
     * Creates an SDK client with bounded I/O and request logging disabled (SDK logs may include credentials).
     *
     * @param path the path
     * @return the result
     */
    protected DingTalkClient client(String path)
    {
        // The OAPI client uses WebV2Utils, independently of DefaultTaobaoClient's WebUtils setting.
        WebV2Utils.setIgnoreSSLCheck(false);
        WebV2Utils.setIgnoreHostCheck(false);
        TaobaoLogger.setNeedEnableLogger(false);
        DefaultDingTalkClient client = new DefaultDingTalkClient("https://oapi.dingtalk.com" + path);
        client.setConnectTimeout(5000);
        client.setReadTimeout(10000);
        client.setNeedEnableLogger(false);
        return client;
    }

    @SuppressWarnings({"PMD.DoNotUseThreads", "PMD.PreserveStackTrace"}) // Bounded backoff honors interruption; SDK exceptions may contain tokens.
    private <T extends TaobaoResponse> T call(String path, TaobaoRequest<T> request, @Nullable String token,
                                              Function<T, Long> code, long deadline)
    {
        for (int attempt = 0; attempt < 3; attempt++) {
            if (System.nanoTime() >= deadline || Thread.currentThread().isInterrupted()) {
                throw unavailable("directory fetch timed out");
            }
            try {
                T response = client(path).execute(request, token);
                Long error = code.apply(response);
                if (Long.valueOf(0).equals(error)) {
                    return response;
                }
                if (attempt == 2 || !(Long.valueOf(88).equals(error) || Long.valueOf(90018).equals(error))) {
                    throw unavailable("API " + path + " returned error " + error);
                }
            }
            catch (ApiException failed) {
                // Exception text can contain the token and request body. Do not attach it to a public/logged exception.
                if (attempt == 2) {
                    throw unavailable("API " + path + " could not be reached");
                }
            }
            try {
                Thread.sleep(200L * (attempt + 1));
            }
            catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw unavailable("directory fetch interrupted");
            }
        }
        throw unavailable("directory fetch failed");
    }

    static GrantForgeException unavailable(String detail)
    {
        return DataSourceErrors.unavailable("DingTalk: " + detail);
    }
}

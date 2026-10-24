// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.solr.agent;

import org.apache.solr.common.params.ModifiableSolrParams;
import org.apache.solr.common.params.SolrParams;
import org.apache.solr.security.AuthorizationContext;
import org.apache.solr.security.PermissionNameProvider;
import org.jspecify.annotations.Nullable;

import java.security.Principal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** A request as Solr hands it to an authorization plugin, built by the tests. */
@SuppressWarnings("PMD.TestClassWithoutTestCases")
final class RequestContext
        extends AuthorizationContext
{
    private @Nullable Principal principal;
    private @Nullable String remote = "10.0.0.7";
    private final Map<String, String> headers = new HashMap<>();
    private final List<CollectionRequest> collections = new ArrayList<>();
    private final ModifiableSolrParams params = new ModifiableSolrParams();
    private @Nullable RequestType type = RequestType.READ;
    private @Nullable String resource = "/select";
    private @Nullable Object handler;

    static RequestContext of(@Nullable String user)
    {
        RequestContext context = new RequestContext();
        context.principal = user == null ? null : () -> user;
        return context;
    }

    RequestContext principal(Principal value)
    {
        principal = value;
        return this;
    }

    RequestContext collections(String... names)
    {
        for (String name : names) {
            collections.add(new CollectionRequest(name));
        }
        return this;
    }

    RequestContext nullCollection()
    {
        collections.add(null);
        collections.add(new CollectionRequest(" "));
        return this;
    }

    RequestContext type(@Nullable RequestType value)
    {
        type = value;
        return this;
    }

    RequestContext resource(@Nullable String value)
    {
        resource = value;
        return this;
    }

    RequestContext permission(PermissionNameProvider.@Nullable Name name)
    {
        handler = (PermissionNameProvider) request -> name;
        return this;
    }

    RequestContext handler(Object value)
    {
        handler = value;
        return this;
    }

    RequestContext param(String name, String... values)
    {
        params.set(name, values);
        return this;
    }

    RequestContext from(@Nullable String address, @Nullable String forwarded)
    {
        remote = address;
        if (forwarded != null) {
            headers.put("X-Forwarded-For", forwarded);
        }
        return this;
    }

    @Override
    public SolrParams getParams()
    {
        return params;
    }

    @Override
    public @Nullable Principal getUserPrincipal()
    {
        return principal;
    }

    @Override
    public @Nullable String getUserName()
    {
        return principal == null ? null : principal.getName();
    }

    @Override
    public @Nullable String getHttpHeader(String name)
    {
        return headers.get(name);
    }

    @Override
    public Enumeration<String> getHeaderNames()
    {
        return Collections.enumeration(headers.keySet());
    }

    @Override
    public @Nullable String getRemoteAddr()
    {
        return remote;
    }

    @Override
    public @Nullable String getRemoteHost()
    {
        return remote;
    }

    @Override
    public List<CollectionRequest> getCollectionRequests()
    {
        return collections;
    }

    @Override
    public @Nullable RequestType getRequestType()
    {
        return type;
    }

    @Override
    public @Nullable String getResource()
    {
        return resource;
    }

    @Override
    public String getHttpMethod()
    {
        return "GET";
    }

    @Override
    public @Nullable Object getHandler()
    {
        return handler;
    }
}

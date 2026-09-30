// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.security;

import org.springframework.security.access.ConfigAttribute;
import org.springframework.security.access.SecurityConfig;
import org.springframework.security.web.access.intercept.FilterInvocationSecurityMetadataSource;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;

@Service
public class GrantForgeFilterInvocationSecurityMetadataSource implements
        FilterInvocationSecurityMetadataSource {

    // This method is used to determine whether the url requested by the user is in the permission table, and if it is in the permission table, it is returned to the decide method to determine whether the user has this permission. Release if not in the permission table.
    // I'm going to direct interception, direct interception, whatever the requested url is, and then in SecurityAccessDecisionManager decide method to intercept or release of decision making.
    // So the return id of this method can't return null and I'm just going to return it here.
    @Override
    public Collection<ConfigAttribute> getAttributes(Object object) throws IllegalArgumentException {
        Collection<ConfigAttribute> config = new ArrayList<>();
        config.add(new SecurityConfig("null"));
        return config;
    }

    @Override
    public Collection<ConfigAttribute> getAllConfigAttributes() {
        return null;
    }

    @Override
    public boolean supports(Class<?> clazz) {
        return true;
    }

}

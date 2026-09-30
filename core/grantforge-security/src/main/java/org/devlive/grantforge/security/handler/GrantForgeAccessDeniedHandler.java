// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.security.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.devlive.grantforge.common.enums.SystemMessageEnums;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
public class GrantForgeAccessDeniedHandler implements AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    public GrantForgeAccessDeniedHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException accessDeniedException) throws IOException, ServletException {
        response.setContentType("application/json;charset=UTF-8");
        Map<String, Object> map = new HashMap<>();
        map.put("message", SystemMessageEnums.SYSTEM_UNAUTHORIZED.getValue());
        map.put("code", SystemMessageEnums.SYSTEM_UNAUTHORIZED.getCode());
        response.setContentType("application/json");
        response.getWriter().write(objectMapper.writeValueAsString(map));
    }

}

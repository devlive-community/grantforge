package org.devlive.grantforge.security;

import lombok.Data;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.devlive.grantforge.common.enums.SystemMessageEnums;
import org.devlive.grantforge.service.entity.MenuEntity;
import org.devlive.grantforge.service.entity.MethodEntity;
import org.devlive.grantforge.service.entity.RoleEntity;
import org.devlive.grantforge.service.repository.MenuRepository;
import org.devlive.grantforge.service.service.MethodService;
import org.devlive.grantforge.service.service.RoleService;
import org.springframework.security.access.AccessDecisionManager;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.ConfigAttribute;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.web.FilterInvocation;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import javax.servlet.http.HttpServletRequest;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Slf4j
@Service
public class GrantForgeAccessDecisionManager implements AccessDecisionManager
{

    private final MenuRepository menuRepository;
    private final MethodService methodService;
    private final RoleService systemRoleService;

    public GrantForgeAccessDecisionManager(MenuRepository menuRepository, MethodService methodService, RoleService systemRoleService)
    {
        this.menuRepository = menuRepository;
        this.methodService = methodService;
        this.systemRoleService = systemRoleService;
    }

    @Override
    public void decide(Authentication authentication, Object object, Collection<ConfigAttribute> configAttributes) throws AccessDeniedException, InsufficientAuthenticationException
    {
        HttpServletRequest request = ((FilterInvocation) object).getHttpRequest();
        String requestUrl = request.getServletPath(), requestMethod = request.getMethod();
        log.info(String.format("Request url <%s> method <%s>", requestUrl, requestMethod));
        MethodEntity methodEntity = this.methodService.getByMethod(requestMethod.toUpperCase());
        // Get whether the data is in the white list through the database
        if (!ObjectUtils.isEmpty(methodEntity)) {
            MenuEntity menuEntity = this.menuRepository.findByUrlAndMethodsContaining(requestUrl, methodEntity);
            if (!ObjectUtils.isEmpty(menuEntity)) {
                return;
            }
        }
        // 用户个人权限查询
        Map<Long, Role> menus = new ConcurrentHashMap<>();
        for (GrantedAuthority grantedAuthority : authentication.getAuthorities()) {
            // TODO：抽取权限过来的数据并解析(目前通过数据库抽取后期加入缓冲中)
            String granted = grantedAuthority.getAuthority();
            if (granted.equals("ROLE_ANONYMOUS")) {
                log.warn("Role " + granted + " is not allowed");
                return;
            }
            RoleEntity roleModel = this.systemRoleService.getModelById(Long.valueOf(granted));
            roleModel.getMenus().forEach(m -> {
                if (ObjectUtils.isEmpty(menus.get(m.getId()))) {
                    if (!m.getUrl().equalsIgnoreCase("#")) {
                        Role role = new Role();
                        role.setUrl(m.getUrl());
                        role.setMethod(StringUtils.join(m.getMethods().stream().map(v -> v.getMethod()).collect(Collectors.toList()), ","));
                        menus.put(m.getId(), role);
                    }
                }
            });
        }
        // 抽取判断授权的菜单列表
        for (Long k : menus.keySet()) {
            Role role = menus.get(k);
            if (role.getUrl().contains("*")) {
                if (role.getMethod().contains(requestMethod) && requestUrl.startsWith(role.getUrl().replace("*", ""))) {
                    return;
                }
            } else {
                if (role.getMethod().contains(requestMethod) && requestUrl.equalsIgnoreCase(role.getUrl())) {
                    return;
                }
            }
        }
        throw new AccessDeniedException(SystemMessageEnums.SYSTEM_UNAUTHORIZED.getValue());
    }

    @Override
    public boolean supports(ConfigAttribute attribute) {
        return true;
    }

    @Override
    public boolean supports(Class<?> clazz) {
        return true;
    }

    @Data
    @ToString
    private class Role {
        private String url;
        private String method;
    }
}

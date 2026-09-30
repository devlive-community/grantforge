// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.controller;

import lombok.extern.slf4j.Slf4j;
import org.devlive.grantforge.common.encryption.EncryptionShaUtils;
import org.devlive.grantforge.common.enums.SystemMessageEnums;
import org.devlive.grantforge.common.page.PageModel;
import org.devlive.grantforge.param.UserBasicParam;
import org.devlive.grantforge.param.page.PageParam;
import org.devlive.grantforge.param.user.UserSetRoleParam;
import org.devlive.grantforge.service.entity.RoleEntity;
import org.devlive.grantforge.service.entity.UserEntity;
import org.devlive.grantforge.service.entity.common.CommonResponseModel;
import org.devlive.grantforge.service.service.UserIService;
import org.springframework.data.domain.Pageable;
import org.springframework.util.ObjectUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping(value = "api/v1/user")
@Slf4j
public class UserController
{
    private final UserIService userService;

    public UserController(UserIService userService)
    {
        this.userService = userService;
    }

    @GetMapping
    public CommonResponseModel getAll(@Validated PageParam param)
    {
        Pageable pageable = PageModel.getPageable(param.getPage(), param.getSize());
        return CommonResponseModel.success(this.userService.getAllByPage(pageable));
    }

    @PostMapping(value = "register")
    public CommonResponseModel add(@RequestBody @Validated UserBasicParam param)
    {
        log.info("执行用户操作 [{}] {}", "新建", param.getUsername());
        if (!ObjectUtils.isEmpty(this.userService.getModelByName(param.getUsername()))) {
            return CommonResponseModel.error(SystemMessageEnums.SYSTEM_USER_EXISTS);
        }
        UserEntity user = new UserEntity();
        user.setName(param.getUsername());
        user.setPassword(EncryptionShaUtils.hash256(param.getPassword()));
        // default active this user
        user.setActive(Boolean.TRUE);
        return CommonResponseModel.success(this.userService.insertModel(user));
    }

    @GetMapping(value = "info/{name}")
    public CommonResponseModel info(@PathVariable String name)
    {
        return CommonResponseModel.success(this.userService.getModelByName(name));
    }

    /**
     * 分配用户权限
     */
    @PutMapping(value = "role")
    public CommonResponseModel setRole(@RequestBody @Validated UserSetRoleParam param)
    {
        UserEntity user = (UserEntity) this.userService.getModelById(Long.valueOf(param.getId()));
        List<RoleEntity> roles = new ArrayList<>();
        param.getValues()
                .forEach(roleId -> {
                    RoleEntity role = new RoleEntity();
                    role.setId(Long.valueOf(roleId));
                    roles.add(role);
                });
        user.setRoles(roles);
        return CommonResponseModel.success(this.userService.insertModel(user));
    }

    @DeleteMapping
    public CommonResponseModel delete(@RequestParam(value = "id") @Validated Long id)
    {
        return CommonResponseModel.success(this.userService.deleteById(id));
    }
}

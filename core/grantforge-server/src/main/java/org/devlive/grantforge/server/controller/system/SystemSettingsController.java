// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.controller.system;

import org.devlive.grantforge.service.entity.common.CommonResponseModel;
import org.devlive.grantforge.service.service.system.SystemSettingsIService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * <p> SystemController </p>
 * <p> Description : SystemController </p>
 * <p> Author : qianmoQ </p>
 * <p> Version : 1.0 </p>
 * <p> Create Time : 2019-01-25 14:41 </p>
 * <p> Author Email: <a href="mailTo:shichengoooo@163.com">qianmoQ</a> </p>
 */
@RestController
@RequestMapping(value = "api/v1/system/settings")
@Slf4j
public class SystemSettingsController {

    @Autowired
    private SystemSettingsIService service;

    @GetMapping
    public CommonResponseModel get() {
        return CommonResponseModel.success(this.service.getModelByActiveTrue());
    }

}

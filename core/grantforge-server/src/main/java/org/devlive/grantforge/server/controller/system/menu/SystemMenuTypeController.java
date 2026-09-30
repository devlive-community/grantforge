/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.devlive.grantforge.server.controller.system.menu;

import org.devlive.grantforge.common.pinyin.PinYinUtils;
import org.devlive.grantforge.service.entity.common.CommonResponseModel;
import org.devlive.grantforge.common.page.PageModel;
import org.devlive.grantforge.service.entity.system.menu.SystemMenuTypeModel;
import org.devlive.grantforge.param.page.PageParam;
import org.devlive.grantforge.param.system.menu.SystemMenuTypeBasicParam;
import org.devlive.grantforge.param.system.menu.SystemMenuTypeSetParam;
import org.devlive.grantforge.service.service.system.menu.SystemMenuTypeIService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Pageable;
import org.springframework.util.ObjectUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * <p> SystemMenuController </p>
 * <p> Description : SystemMenuController </p>
 * <p> Author : qianmoQ </p>
 * <p> Version : 1.0 </p>
 * <p> Create Time : 2019-01-26 15:56 </p>
 * <p> Author Email: <a href="mailTo:shichengoooo@163.com">qianmoQ</a> </p>
 */
@RestController
@RequestMapping(value = "api/v1/system/menu/type")
@Slf4j
public class SystemMenuTypeController {

    @Autowired
    private SystemMenuTypeIService systemMenuTypeService;

    /**
     * get all menu type by user and page
     *
     * @param param page info
     * @return all menu by page and user
     */
    @GetMapping
    public CommonResponseModel list(@Validated PageParam param) {
        Pageable pageable = PageModel.getPageable(param.getPage(), param.getSize());
        return CommonResponseModel.success(this.systemMenuTypeService.getAllByPage(pageable));
    }

    /**
     * add a menu type to system
     *
     * @param param menu type info
     * @return add response
     */
    @PostMapping
    public CommonResponseModel add(@RequestBody @Validated SystemMenuTypeBasicParam param) {
        SystemMenuTypeModel systemRole = new SystemMenuTypeModel();
        if (ObjectUtils.isEmpty(param.getActive())) {
            systemRole.setActive(Boolean.TRUE);
        }
        systemRole.setDescription(param.getDescription());
        systemRole.setName(param.getName());
        systemRole.setCode(PinYinUtils.getFullFirstToUpper(param.getName()));
        return CommonResponseModel.success(this.systemMenuTypeService.insertModel(systemRole));
    }

    @PutMapping
    public CommonResponseModel put(@RequestBody @Validated SystemMenuTypeSetParam param) {
        SystemMenuTypeModel systemRole = new SystemMenuTypeModel();
        BeanUtils.copyProperties(param, systemRole);
        systemRole.setId(Long.valueOf(param.getId()));
        systemRole.setCode(PinYinUtils.getFullFirstToUpper(param.getName()));
        return CommonResponseModel.success(this.systemMenuTypeService.insertModel(systemRole));
    }

}

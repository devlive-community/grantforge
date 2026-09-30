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
package org.devlive.grantforge.server.controller.system.log;

import org.devlive.grantforge.common.pinyin.PinYinUtils;
import org.devlive.grantforge.service.entity.common.CommonResponseModel;
import org.devlive.grantforge.common.page.PageModel;
import org.devlive.grantforge.service.entity.system.log.SystemLogTypeModel;
import org.devlive.grantforge.param.page.PageParam;
import org.devlive.grantforge.param.system.log.SystemLogTypeCreateParam;
import org.devlive.grantforge.param.system.log.SystemLogTypeSetParam;
import org.devlive.grantforge.service.service.system.log.SystemLogTypeIService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Pageable;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * <p> SystemLogTypeController </p>
 * <p> Description : SystemLogTypeController </p>
 * <p> Author : qianmoQ </p>
 * <p> Version : 1.0 </p>
 * <p> Create Time : 2019-05-07 14:34 </p>
 * <p> Author Email: <a href="mailTo:shichengoooo@163.com">qianmoQ</a> </p>
 */
@RestController
@RequestMapping(value = "api/v1/system/log/type")
@Slf4j
public class SystemLogTypeController {

    @Autowired
    private SystemLogTypeIService systemLogTypeService;

    @GetMapping
    public CommonResponseModel getAll(@Validated PageParam param) {
        Pageable pageable = PageModel.getPageable(param.getPage(), param.getSize());
        return CommonResponseModel.success(this.systemLogTypeService.getAllByPage(pageable));
    }

    @PostMapping
    public CommonResponseModel add(@RequestBody @Validated SystemLogTypeCreateParam param) {
        SystemLogTypeModel logType = new SystemLogTypeModel();
        BeanUtils.copyProperties(param, logType);
        logType.setCode(PinYinUtils.getFullFirstToUpper(param.getName()));
        return CommonResponseModel.success(this.systemLogTypeService.insertModel(logType));
    }

    @PutMapping
    public CommonResponseModel put(@RequestBody @Validated SystemLogTypeSetParam param) {
        SystemLogTypeModel logType = new SystemLogTypeModel();
        BeanUtils.copyProperties(param, logType);
        logType.setId(Long.valueOf(param.getId()));
        logType.setCode(PinYinUtils.getFullFirstToUpper(param.getName()));
        return CommonResponseModel.success(this.systemLogTypeService.insertModel(logType));
    }

}

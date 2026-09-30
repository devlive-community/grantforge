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
package org.devlive.grantforge.server.controller.icon;

import org.devlive.grantforge.service.entity.common.CommonResponseModel;
import org.devlive.grantforge.service.entity.icon.IconModel;
import org.devlive.grantforge.service.entity.icon.IconTypeModel;
import org.devlive.grantforge.service.entity.icon.IconUsageModel;
import org.devlive.grantforge.common.page.PageModel;
import org.devlive.grantforge.param.icon.IconCreateParam;
import org.devlive.grantforge.param.icon.IconSetParam;
import org.devlive.grantforge.param.page.PageParam;
import org.devlive.grantforge.service.service.icon.IconIService;
import org.devlive.grantforge.service.service.icon.IconTypeIService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Pageable;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * <p> IconController </p>
 * <p> Description : IconController </p>
 * <p> Author : qianmoQ </p>
 * <p> Version : 1.0 </p>
 * <p> Create Time : 2019-05-08 18:06 </p>
 * <p> Author Email: <a href="mailTo:shichengoooo@163.com">qianmoQ</a> </p>
 */
@RestController
@RequestMapping(value = "api/v1/icon")
@Slf4j
public class IconController {

    @Autowired
    private IconIService service;

    @Autowired
    private IconTypeIService iconTypeService;

    @GetMapping
    public CommonResponseModel getAll(@Validated PageParam param) {
        Pageable pageable = PageModel.getPageable(param.getPage(), param.getSize());
        return CommonResponseModel.success(this.service.getAllByPage(pageable));
    }

    @PostMapping
    public CommonResponseModel add(@RequestBody @Validated IconCreateParam param) {
        IconModel model = new IconModel();
        BeanUtils.copyProperties(param, model);
        IconTypeModel type = new IconTypeModel();
        type.setId(Long.valueOf(param.getType()));
        model.setType(type);
        IconUsageModel usage = new IconUsageModel();
        usage.setId(Long.valueOf(param.getUsage()));
        model.setUsage(usage);
        return CommonResponseModel.success(this.service.insertModel(model));
    }

    @PutMapping
    public CommonResponseModel put(@RequestBody @Validated IconSetParam param) {
        IconModel model = new IconModel();
        BeanUtils.copyProperties(param, model);
        model.setId(Long.valueOf(param.getId()));
        IconTypeModel type = (IconTypeModel) this.iconTypeService.getModelById(Long.valueOf(param.getType()));
        model.setType(type);
        return CommonResponseModel.success(this.service.insertModel(model));
    }

}

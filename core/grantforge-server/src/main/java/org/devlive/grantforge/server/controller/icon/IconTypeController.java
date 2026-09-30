// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.controller.icon;

import org.devlive.grantforge.common.pinyin.PinYinUtils;
import org.devlive.grantforge.service.entity.common.CommonResponseModel;
import org.devlive.grantforge.service.entity.icon.IconTypeModel;
import org.devlive.grantforge.common.page.PageModel;
import org.devlive.grantforge.param.icon.IconTypeCreateParam;
import org.devlive.grantforge.param.icon.IconTypeSetParam;
import org.devlive.grantforge.param.page.PageParam;
import org.devlive.grantforge.service.service.icon.IconTypeIService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Pageable;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * <p> IconTypeController </p>
 * <p> Description : IconTypeController </p>
 * <p> Author : qianmoQ </p>
 * <p> Version : 1.0 </p>
 * <p> Create Time : 2019-05-08 18:06 </p>
 * <p> Author Email: <a href="mailTo:shichengoooo@163.com">qianmoQ</a> </p>
 */
@RestController
@RequestMapping(value = "api/v1/icon/type")
@Slf4j
public class IconTypeController {

    @Autowired
    private IconTypeIService iconTypeService;

    @GetMapping
    public CommonResponseModel getAll(@Validated PageParam param) {
        Pageable pageable = PageModel.getPageable(param.getPage(), param.getSize());
        return CommonResponseModel.success(this.iconTypeService.getAllByPage(pageable));
    }

    @PostMapping
    public CommonResponseModel add(@RequestBody @Validated IconTypeCreateParam param) {
        IconTypeModel iconType = new IconTypeModel();
        BeanUtils.copyProperties(param, iconType);
        iconType.setCode(PinYinUtils.getFullFirstToUpper(param.getName()));
        return CommonResponseModel.success(this.iconTypeService.insertModel(iconType));
    }

    @PutMapping
    public CommonResponseModel put(@RequestBody @Validated IconTypeSetParam param) {
        IconTypeModel iconType = new IconTypeModel();
        BeanUtils.copyProperties(param, iconType);
        iconType.setId(Long.valueOf(param.getId()));
        iconType.setCode(PinYinUtils.getFullFirstToUpper(param.getName()));
        return CommonResponseModel.success(this.iconTypeService.insertModel(iconType));
    }

}

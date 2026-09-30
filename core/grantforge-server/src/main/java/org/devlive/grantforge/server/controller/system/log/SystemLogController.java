// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.controller.system.log;

import lombok.extern.slf4j.Slf4j;
import org.devlive.grantforge.common.pinyin.PinYinUtils;
import org.devlive.grantforge.param.system.log.SystemLogTypeCreateParam;
import org.devlive.grantforge.param.system.log.SystemLogTypeSetParam;
import org.devlive.grantforge.service.entity.common.CommonResponseModel;
import org.devlive.grantforge.service.entity.system.log.SystemLogTypeModel;
import org.devlive.grantforge.service.service.system.log.SystemLogIService;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
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
@RequestMapping(value = "api/v1/system/log")
@Slf4j
public class SystemLogController {

    @Autowired
    private SystemLogIService systemLogService;

//    @Autowired
//    private SystemLogToMongoDbService mongoDbService;

//    @GetMapping
//    public CommonResponseModel getAll(@Validated PageParam param) {
//        Pageable pageable = PageModel.getPageable(param.getPage(), param.getSize());
//        return CommonResponseModel.success(this.mongoDbService.getAllByPage(pageable));
//    }

    @PostMapping
    public CommonResponseModel add(@RequestBody @Validated SystemLogTypeCreateParam param) {
        SystemLogTypeModel logType = new SystemLogTypeModel();
        BeanUtils.copyProperties(param, logType);
        logType.setCode(PinYinUtils.getFullFirstToUpper(param.getName()));
        return null;
    }

    @PutMapping
    public CommonResponseModel put(@RequestBody @Validated SystemLogTypeSetParam param) {
        SystemLogTypeModel logType = new SystemLogTypeModel();
        BeanUtils.copyProperties(param, logType);
        logType.setId(Long.valueOf(param.getId()));
        logType.setCode(PinYinUtils.getFullFirstToUpper(param.getName()));
        return null;
    }

    /**
     * 查询日志详情
     *
     * @param primaryKey 数据主键
     * @return 日志详情
     */
//    @GetMapping(value = "/details")
//    public CommonResponseModel infoDetail(@RequestParam(value = "primaryKey") String primaryKey) {
//        return CommonResponseModel.success(this.mongoDbService.getModelById(primaryKey));
//    }
}

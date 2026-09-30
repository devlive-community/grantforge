// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.validation.system.log;

import org.devlive.grantforge.service.service.system.log.SystemLogTypeIService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;

import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorContext;

/**
 * <p> SystemLogTypeRequireValidationValidator </p>
 * <p> Description : SystemLogTypeRequireValidationValidator </p>
 * <p> Author : qianmoQ </p>
 * <p> Version : 1.0 </p>
 * <p> Create Time : 2019-01-26 15:02 </p>
 * <p> Author Email: <a href="mailTo:shichengoooo@163.com">qianmoQ</a> </p>
 */
@Slf4j
public class SystemLogTypeRequireValidationValidator implements ConstraintValidator<SystemLogTypeRequireValidation, String> {

    @Autowired
    private SystemLogTypeIService systemLogTypeService;

    @Override
    public void initialize(SystemLogTypeRequireValidation validation) {
    }

    @Override
    public boolean isValid(String s, ConstraintValidatorContext context) {
        log.info("validation system log type id is exists, id is {}", s);
        return !StringUtils.isEmpty(s) && !ObjectUtils.isEmpty(this.systemLogTypeService.getModelById(Long.valueOf(s)));
    }

}

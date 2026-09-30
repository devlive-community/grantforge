// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.validation.system.menu;

import org.devlive.grantforge.service.service.system.menu.SystemMenuTypeIService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;

import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorContext;

/**
 * <p> SystemRoleValidationValidator </p>
 * <p> Description : SystemRoleValidationValidator </p>
 * <p> Author : qianmoQ </p>
 * <p> Version : 1.0 </p>
 * <p> Create Time : 2019-01-26 15:02 </p>
 * <p> Author Email: <a href="mailTo:shichengoooo@163.com">qianmoQ</a> </p>
 */
@Slf4j
public class SystemMenuTypeRequireValidationValidator implements ConstraintValidator<SystemMenuTypeRequireValidation, String> {

    @Autowired
    private SystemMenuTypeIService systemMenuTypeService;

    @Override
    public void initialize(SystemMenuTypeRequireValidation validation) {
    }

    @Override
    public boolean isValid(String s, ConstraintValidatorContext context) {
        log.info("validation system menu type id is exists, id is {}", s);
        return !StringUtils.isEmpty(s) && !ObjectUtils.isEmpty(this.systemMenuTypeService.getModelById(Long.valueOf(s)));
    }

}

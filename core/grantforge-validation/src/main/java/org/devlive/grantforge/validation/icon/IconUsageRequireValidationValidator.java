// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.validation.icon;

import org.devlive.grantforge.service.service.icon.IconUsageIService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;

import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorContext;

/**
 * <p> IconUsageRequireValidationValidator </p>
 * <p> Description : IconUsageRequireValidationValidator </p>
 * <p> Author : qianmoQ </p>
 * <p> Version : 1.0 </p>
 * <p> Create Time : 2019-01-26 15:02 </p>
 * <p> Author Email: <a href="mailTo:shichengoooo@163.com">qianmoQ</a> </p>
 */
@Slf4j
public class IconUsageRequireValidationValidator implements ConstraintValidator<IconUsageRequireValidation, String> {

    @Autowired
    private IconUsageIService service;

    @Override
    public void initialize(IconUsageRequireValidation validation) {
    }

    @Override
    public boolean isValid(String s, ConstraintValidatorContext context) {
        log.info("validation id is exists, id is {}", s);
        return !StringUtils.isEmpty(s) && !ObjectUtils.isEmpty(this.service.getModelById(Long.valueOf(s)));
    }

}

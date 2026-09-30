// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.validation.user;

import org.devlive.grantforge.service.service.UserIService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;

import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorContext;

/**
 * <p> UserRequireValidationValidator </p>
 * <p> Description : UserRequireValidationValidator </p>
 * <p> Author : qianmoQ </p>
 * <p> Version : 1.0 </p>
 * <p> Create Time : 2019-01-26 14:58 </p>
 * <p> Author Email: <a href="mailTo:shichengoooo@163.com">qianmoQ</a> </p>
 */
@Slf4j
public class UserRequireValidationValidator implements ConstraintValidator<UserRequireValidation, String> {

    @Autowired
    private UserIService userService;

    @Override
    public void initialize(UserRequireValidation validation) {
    }

    @Override
    public boolean isValid(String s, ConstraintValidatorContext context) {
        log.info("validation user id is exists, user id is {}", s);
        return !StringUtils.isEmpty(s) && !ObjectUtils.isEmpty(this.userService.getModelById(Long.valueOf(s)));
    }

}

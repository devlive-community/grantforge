// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.validation.user;

import lombok.extern.slf4j.Slf4j;
import org.devlive.grantforge.service.service.UserIService;
import org.springframework.util.ObjectUtils;

import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorContext;

@Slf4j
public class CheckUserNameValidator implements ConstraintValidator<CheckUserNameValidation, String>
{
    private final UserIService userService;

    public CheckUserNameValidator(UserIService userService)
    {
        this.userService = userService;
    }

    @Override
    public void initialize(CheckUserNameValidation validation)
    {
    }

    @Override
    public boolean isValid(String s, ConstraintValidatorContext context)
    {
        log.info("validation username is exists, username is {}", s);
        return ObjectUtils.isEmpty(this.userService.getModelByName(s));
    }

}

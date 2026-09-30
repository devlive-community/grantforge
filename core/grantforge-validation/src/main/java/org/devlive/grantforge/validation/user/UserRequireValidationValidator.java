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

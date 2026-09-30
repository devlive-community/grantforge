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

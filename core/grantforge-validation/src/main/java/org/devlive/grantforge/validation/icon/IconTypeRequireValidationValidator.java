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
package org.devlive.grantforge.validation.icon;

import org.devlive.grantforge.service.service.icon.IconTypeIService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;

import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorContext;

/**
 * <p> IconTypeRequireValidationValidator </p>
 * <p> Description : IconTypeRequireValidationValidator </p>
 * <p> Author : qianmoQ </p>
 * <p> Version : 1.0 </p>
 * <p> Create Time : 2019-01-26 15:02 </p>
 * <p> Author Email: <a href="mailTo:shichengoooo@163.com">qianmoQ</a> </p>
 */
@Slf4j
public class IconTypeRequireValidationValidator implements ConstraintValidator<IconTypeRequireValidation, String> {

    @Autowired
    private IconTypeIService IconTypeService;

    @Override
    public void initialize(IconTypeRequireValidation validation) {
    }

    @Override
    public boolean isValid(String s, ConstraintValidatorContext context) {
        log.info("validation system log type id is exists, id is {}", s);
        return !StringUtils.isEmpty(s) && !ObjectUtils.isEmpty(this.IconTypeService.getModelById(Long.valueOf(s)));
    }

}

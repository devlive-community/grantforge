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
package org.devlive.grantforge.aop.validation.user;

import org.devlive.grantforge.common.enums.SystemMessageEnums;
import org.devlive.grantforge.common.enums.UserMessageEnums;
import org.devlive.grantforge.service.entity.UserEntity;
import org.devlive.grantforge.service.service.UserIService;
import com.google.gson.Gson;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.ObjectUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * <p> UserRequiredParamPathAndQueryAopValidationAspet </p>
 * <p> Description : UserRequiredParamPathAndQueryAopValidationAspet </p>
 * <p> Author : qianmoQ </p>
 * <p> Version : 1.0 </p>
 * <p> Create Time : 2019-03-18 11:11 </p>
 * <p> Author Email: <a href="mailTo:shichengoooo@163.com">qianmoQ</a> </p>
 */
@Aspect
@Component
@Slf4j
public class UserRequiredParamPathAndQueryAopValidationAspet {

    @Autowired
    private UserIService userService;

    @Before(value = "@annotation(paramPathAndQueryAopValidation)")
    public void paramValidation(JoinPoint point, UserRequiredParamPathAndQueryAopValidation paramPathAndQueryAopValidation) throws IOException {
        Object[] paramObj = point.getArgs();
        ServletRequestAttributes request = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        HttpServletResponse response = request.getResponse();
        response.setContentType("application/json;charset=UTF-8");
        Map<String, Object> map = new HashMap<>();
        OutputStream output = null;
        output = response.getOutputStream();
        if (paramObj.length > 0) {
            UserEntity user = (UserEntity) this.userService.getModelById(Long.valueOf(paramObj[0].toString()));
            if (ObjectUtils.isEmpty(user)) {
                map.put("message", UserMessageEnums.USER_NOT_FOUND.getValue());
                map.put("code", UserMessageEnums.USER_NOT_FOUND.getCode());
                output.write(new Gson().toJson(map).getBytes("UTF-8"));
                if (output != null) {
                    output.close();
                }
            }
        } else {
            map.put("message", SystemMessageEnums.SYSTEM_PARAM_PATH_MUST_NULL.getValue());
            map.put("code", SystemMessageEnums.SYSTEM_PARAM_PATH_MUST_NULL.getCode());
            output.write(new Gson().toJson(map).getBytes("UTF-8"));
            if (output != null) {
                output.close();
            }
        }
    }

}

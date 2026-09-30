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
package org.devlive.grantforge.service.entity.common;

import org.devlive.grantforge.common.enums.SystemMessageEnums;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;

/**
 * <p> CommonResponseModel </p>
 * <p> Description : CommonResponseModel </p>
 * <p> Author : qianmoQ </p>
 * <p> Version : 1.0 </p>
 * <p> Create Time : 2019-01-25 10:18 </p>
 * <p> Author Email: <a href="mailTo:shichengoooo@163.com">qianmoQ</a> </p>
 */
@Data
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class CommonResponseModel<T> {

    private int code;
    private String message;
    private T data;

    public static CommonResponseModel success() {
        return success(SystemMessageEnums.SYSTEM_SUCCESS, null);
    }

    public static <T> CommonResponseModel success(T data) {
        return success(SystemMessageEnums.SYSTEM_SUCCESS, data);
    }

    public static <T> CommonResponseModel success(SystemMessageEnums message) {
        return success(message, null);
    }

    public static <T> CommonResponseModel success(SystemMessageEnums message, T data) {
        CommonResponseModel result = new CommonResponseModel();
        result.setCodeMessage(message);
        result.setData(data);
        return result;
    }

    public static CommonResponseModel validateError() {
        return validateError(null);
    }

    public static CommonResponseModel validateError(SystemMessageEnums message) {
        CommonResponseModel result = new CommonResponseModel();
        result.setCodeMessage(message);
        result.setData(null);
        return result;
    }

    public static <T> CommonResponseModel validateError(T data) {
        CommonResponseModel result = new CommonResponseModel();
        result.setCodeMessage(SystemMessageEnums.SYSTEM_PARAMS_VALIDATE_ERROR);
        result.setData(data);
        return result;
    }

    public static <T> CommonResponseModel error(SystemMessageEnums message, T data) {
        CommonResponseModel result = new CommonResponseModel();
        result.setCodeMessage(message);
        result.setData(data);
        return result;
    }

    public static <T> CommonResponseModel error(SystemMessageEnums message) {
        return error(message, null);
    }

    /**
     * 校验参数是否为空
     *
     * @param object 校验的参数
     * @return 校验结果
     */
    public static CommonResponseModel validateCheck(Object object) {
        if (ObjectUtils.isEmpty(object) || StringUtils.isEmpty(object)) {
            return CommonResponseModel.error(SystemMessageEnums.SYSTEM_PARAMS_NOT_NULL);
        }
        return null;
    }

    private void setCodeMessage(SystemMessageEnums enums) {
        this.code = enums.getCode();
        this.message = enums.getValue();
    }

}

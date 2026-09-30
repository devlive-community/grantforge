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
package org.devlive.grantforge.common.validation;

import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * <p> ValidationUtils </p>
 * <p> Description : ValidationUtils </p>
 * <p> Author : qianmoQ </p>
 * <p> Version : 1.0 </p>
 * <p> Create Time : 2019-01-25 15:13 </p>
 * <p> Author Email: <a href="mailTo:shichengoooo@163.com">qianmoQ</a> </p>
 */
public class ValidationUtils {

    /**
     * get error information
     *
     * @param result error information
     * @return error list
     */
    public static Map<String, Object> extractValidate(BindingResult result) {
        Map<String, Object> error = new ConcurrentHashMap<>();
        List<FieldError> allErrors = result.getFieldErrors();
        error.put("count", allErrors.size());
        List<Map<String, Object>> fields = new ArrayList<>();
        allErrors.forEach(v -> {
            Map<String, Object> field = new ConcurrentHashMap<>();
            field.put("field", v.getField());
            field.put("message", checkException(v.getDefaultMessage()));
            fields.add(field);
        });
        error.put("error", fields);
        return error;
    }

    private static String checkException(String data) {
        return data;
    }

}
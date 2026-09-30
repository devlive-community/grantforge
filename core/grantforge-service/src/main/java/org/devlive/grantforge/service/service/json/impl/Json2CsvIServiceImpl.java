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
package org.devlive.grantforge.service.service.json.impl;

import org.devlive.grantforge.common.enums.SystemMessageEnums;
import org.devlive.grantforge.common.json.JsonParseUtils;
import org.devlive.grantforge.common.json.JsonValidateUtils;
import org.devlive.grantforge.common.office.CsvUtils;
import org.devlive.grantforge.common.page.PageModel;
import org.devlive.grantforge.service.entity.common.CommonResponseModel;
import org.devlive.grantforge.service.service.json.Json2CsvIService;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

/**
 * <p> Json2CsvServiceImpl </p>
 * <p> Description : Json2CsvServiceImpl </p>
 * <p> Author : qianmoQ </p>
 * <p> Version : 1.0 </p>
 * <p> Create Time : 2019-06-17 19:23 </p>
 * <p> Author Email: <a href="mailTo:shichengoooo@163.com">qianmoQ</a> </p>
 */
@Service(value = "json2CsvService")
public class Json2CsvIServiceImpl implements Json2CsvIService
{

    @Override
    public Long insertModel(Object model) {
        return null;
    }

    @Override
    public Object getModelById(Long id) {
        return null;
    }

    @Override
    public PageModel getAllByPage(Pageable pageable) {
        return null;
    }

    @Override
    public long getCount() {
        return 0;
    }

    @Override
    public CommonResponseModel toCSV(String json) {
        if (!JsonValidateUtils.isJSON(json)) {
            return CommonResponseModel.error(SystemMessageEnums.SYSTEM_JSON_ERROR);
        }
        return CommonResponseModel.success(CsvUtils.getCSV(JsonParseUtils.parseJson(json)));
    }

}

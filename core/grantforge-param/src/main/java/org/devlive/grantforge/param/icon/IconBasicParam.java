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
package org.devlive.grantforge.param.icon;

import org.devlive.grantforge.validation.icon.IconTypeRequireValidation;
import org.devlive.grantforge.validation.icon.IconUsageRequireValidation;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.hibernate.validator.constraints.NotEmpty;

/**
 * <p> IconTypeBasicParam </p>
 * <p> Description : IconTypeBasicParam </p>
 * <p> Author : qianmoQ </p>
 * <p> Version : 1.0 </p>
 * <p> Create Time : 2019-04-26 16:20 </p>
 * <p> Author Email: <a href="mailTo:shichengoooo@163.com">qianmoQ</a> </p>
 */
@Data
@ToString
@AllArgsConstructor
@NoArgsConstructor
public class IconBasicParam {

    @NotEmpty(message = "icon name must not null")
    private String name;

    @NotEmpty(message = "icon description must not null")
    private String description;

    private String zhName; // 图标中文名

    private Boolean active;

    @NotEmpty(message = "icon must not null")
    private String code;

    @NotEmpty(message = "icon type id must not null")
    @IconTypeRequireValidation
    private String type; // 图标类型id

    @NotEmpty(message = "icon usage id must not null")
    @IconUsageRequireValidation
    private String usage; // 图标类型id

}

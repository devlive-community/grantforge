// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

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

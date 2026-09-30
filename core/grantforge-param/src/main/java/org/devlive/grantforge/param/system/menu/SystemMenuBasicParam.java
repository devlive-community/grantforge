// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.param.system.menu;

import org.devlive.grantforge.validation.icon.IconRequireValidation;
import org.devlive.grantforge.validation.system.menu.SystemMenuTypeRequireValidation;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.hibernate.validator.constraints.NotEmpty;

import javax.validation.constraints.Min;
import java.util.List;

/**
 * <p> SystemRoleBasicParam </p>
 * <p> Description : SystemRoleBasicParam </p>
 * <p> Author : qianmoQ </p>
 * <p> Version : 1.0 </p>
 * <p> Create Time : 2019-01-26 16:20 </p>
 * <p> Author Email: <a href="mailTo:shichengoooo@163.com">qianmoQ</a> </p>
 */
@Data
@ToString
@AllArgsConstructor
@NoArgsConstructor
public class SystemMenuBasicParam {

    @NotEmpty(message = "system menu name must not null")
    private String name;

    @NotEmpty(message = "system menu url must not null")
    private String url;

    @NotEmpty(message = "system menu icon must not null")
    private String icon;

    @Min(message = "system menu sorting must 1 or larger", value = 1)
    private Integer sorted;

    @Min(message = "system menu sorting must 1 or larger", value = 1)
    private Integer level;

    @NotEmpty(message = "system menu tips must not null")
    private String tips;

    private Boolean newd = false; // default new feature

    private Long parent; // TODO: not set

    @NotEmpty(message = "system menu method must not null")
    private List<String> method; // get, put, delete, post, and other

    private String description;

    @NotEmpty(message = "system menu type must not null")
    @SystemMenuTypeRequireValidation
    private String type;

    @NotEmpty(message = "icon id must not null")
    @IconRequireValidation
    private String iconId;

}

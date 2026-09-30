// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.param.common;

import org.devlive.grantforge.validation.system.menu.SystemMenuTypeRequireValidation;
import org.devlive.grantforge.validation.system.role.SystemRoleRequireValidation;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.hibernate.validator.constraints.NotEmpty;

/**
 * <p> PageParam </p>
 * <p> Description : PageParam </p>
 * <p> Author : qianmoQ </p>
 * <p> Version : 1.0 </p>
 * <p> Create Time : 2019-03-16 01:20 </p>
 * <p> Author Email: <a href="mailTo:shichengoooo@163.com">qianmoQ</a> </p>
 */
@Data
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class CommonMenuAndRoleParam {

    @NotEmpty(message = "role must not null")
    @SystemRoleRequireValidation
    private String role;

    @NotEmpty(message = "menu type must not null")
    @SystemMenuTypeRequireValidation
    private String menuType;

}

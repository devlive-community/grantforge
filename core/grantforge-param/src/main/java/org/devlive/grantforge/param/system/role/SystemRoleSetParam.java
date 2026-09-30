// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.param.system.role;

import org.devlive.grantforge.validation.system.role.SystemRoleRequireValidation;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.hibernate.validator.constraints.NotEmpty;

/**
 * <p> SystemRoleRequiredParam </p>
 * <p> Description : SystemRoleRequiredParam </p>
 * <p> Author : qianmoQ </p>
 * <p> Version : 1.0 </p>
 * <p> Create Time : 2019-03-20 14:01 </p>
 * <p> Author Email: <a href="mailTo:shichengoooo@163.com">qianmoQ</a> </p>
 */
@Data
@ToString
@AllArgsConstructor
@NoArgsConstructor
public class SystemRoleSetParam {

    @NotEmpty(message = "the role id must not null")
    @SystemRoleRequireValidation
    private String id;

    @NotEmpty(message = "system role name must not null")
    private String name;

    @NotEmpty(message = "system role description must not null")
    private String description;

}

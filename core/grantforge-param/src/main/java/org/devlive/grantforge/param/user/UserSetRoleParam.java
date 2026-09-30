// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.param.user;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.devlive.grantforge.validation.user.UserRequireValidation;
import org.hibernate.validator.constraints.NotEmpty;

import java.util.List;

/**
 * <p> UserBasicParam </p>
 * <p> Description : UserBasicParam </p>
 * <p> Author : qianmoQ </p>
 * <p> Version : 1.0 </p>
 * <p> Create Time : 2019-01-25 10:22 </p>
 * <p> Author Email: <a href="mailTo:shichengoooo@163.com">qianmoQ</a> </p>
 */
@Data
@ToString
@AllArgsConstructor
@NoArgsConstructor
public class UserSetRoleParam
{

    @NotEmpty(message = "user id must not null")
    @UserRequireValidation
    private String id;

    @NotEmpty(message = "路由标记不能为空")
//    @SystemRoleRequireValidation
    private List<Long> values;
}

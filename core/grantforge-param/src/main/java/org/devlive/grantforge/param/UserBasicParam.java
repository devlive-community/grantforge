// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.param;

import org.devlive.grantforge.validation.user.CheckUserNameValidation;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.hibernate.validator.constraints.NotEmpty;

@Data
@ToString
@AllArgsConstructor
@NoArgsConstructor
public class UserBasicParam
{

    @NotEmpty(message = "user name must not null")
    @CheckUserNameValidation
    private String username;

    @NotEmpty(message = "user password must not null")
    private String password;

    private String email;
    private String repassword;
}

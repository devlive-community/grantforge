// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.param.system.role;

import org.devlive.grantforge.validation.system.menu.SystemMenuTypeRequireValidation;
import org.devlive.grantforge.validation.system.role.SystemRoleRequireValidation;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.hibernate.validator.constraints.NotEmpty;

import java.util.List;

@Data
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class SystemRoleMenuParam {

    @NotEmpty(message = "role must not null")
    @SystemRoleRequireValidation
    private Long roleId;

    private List<Long> menus; // menu list

    @NotEmpty(message = "menu type must not null")
    @SystemMenuTypeRequireValidation
    private String menuType;
}

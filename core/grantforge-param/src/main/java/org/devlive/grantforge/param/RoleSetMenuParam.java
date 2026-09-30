package org.devlive.grantforge.param;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.devlive.grantforge.validation.system.role.SystemRoleRequireValidation;
import org.hibernate.validator.constraints.NotEmpty;

@Data
@ToString
@AllArgsConstructor
@NoArgsConstructor
public class RoleSetMenuParam
{

    @NotEmpty(message = "路由不能为空")
    @SystemRoleRequireValidation
    private String id;

    @NotEmpty(message = "菜单不能为空")
    private String menu; // multiple menu id split by ,
}
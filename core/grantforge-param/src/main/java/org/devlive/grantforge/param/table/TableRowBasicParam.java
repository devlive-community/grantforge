// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.param.table;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.hibernate.validator.constraints.NotEmpty;

/**
 * <p> SystemLogTypeBasicParam </p>
 * <p> Description : SystemLogTypeBasicParam </p>
 * <p> Author : qianmoQ </p>
 * <p> Version : 1.0 </p>
 * <p> Create Time : 2019-04-26 16:20 </p>
 * <p> Author Email: <a href="mailTo:shichengoooo@163.com">qianmoQ</a> </p>
 */
@Data
@ToString
@AllArgsConstructor
@NoArgsConstructor
public class TableRowBasicParam {

    @NotEmpty(message = "表头名称不能为空")
    private String title;

    @NotEmpty(message = "表头对应数据的字段不能为空")
    private String properties; // 对应数据的字段

    private Boolean checked = false; // 选中状态
    private String type; // 字段类型,后期支持排序等功能
    private Integer sorted; // 排列顺序

    private Boolean active = true;

    private String[] menus; // 可使用的菜单列表标志

}

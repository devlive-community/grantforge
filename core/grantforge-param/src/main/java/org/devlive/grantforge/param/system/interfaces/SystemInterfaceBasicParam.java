// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.param.system.interfaces;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.hibernate.validator.constraints.NotEmpty;

import java.util.List;

/**
 * <p> SystemInterfaceModel </p>
 * <p> Description : SystemInterfaceModel </p>
 * <p> Author : qianmoQ </p>
 * <p> Version : 1.0 </p>
 * <p> Create Time : 2019-01-25 14:54 </p>
 * <p> Author Email: <a href="mailTo:shichengoooo@163.com">qianmoQ</a> </p>
 */
@Data
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class SystemInterfaceBasicParam {

    @NotEmpty(message = "interface name must not null")
    private String name;

    private String description;
    private Boolean active;
    private Boolean white; // is white list
    private Boolean system; // system default

    @NotEmpty(message = "path must not null")
    private String path;

    @NotEmpty(message = "method must not null")
    private List<String> method; // multiple method split by ,

}

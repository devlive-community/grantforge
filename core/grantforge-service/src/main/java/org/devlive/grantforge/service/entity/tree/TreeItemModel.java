// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.service.entity.tree;

import lombok.Data;
import lombok.ToString;

/**
 * <p> SysteMenuTreeItemModel </p>
 * <p> Description : 针对于tree-ngx插件定制 </p>
 * <p> Author : qianmoQ </p>
 * <p> Version : 1.0 </p>
 * <p> Create Time : 2019-04-30 11:50 </p>
 * <p> Author Email: <a href="mailTo:shichengoooo@163.com">qianmoQ</a> </p>
 */
@Data
@ToString
public class TreeItemModel {

    private Long phrase;

    private TreeItemModel() {
    }

    public static TreeItemModel buildNew() {
        return new TreeItemModel();
    }

}

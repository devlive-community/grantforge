// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.service.entity.tree;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.util.List;

@Data
@ToString
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class TreeModel {

    List<TreeModel> children; // 子数据
    private Long id; // 数据唯一标志
    private String title; // 数据名称
    private String url; // 数据连接地址
    private Integer sorted; // 数据排序规则
    private Boolean newd; // 数据新旧标志
    private String icon; // 数据图标
    private String tips; // 数据提示内容
    private String code; // 数据唯一编码
    private Boolean checked = false; // 是否选中
    private Boolean selected = false; // 是否选中
    private TreeItemModel item;// 针对于tree-ngx插件定制

}

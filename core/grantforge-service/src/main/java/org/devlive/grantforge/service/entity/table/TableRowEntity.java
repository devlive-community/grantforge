// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.service.entity.table;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.devlive.grantforge.service.entity.BaseEntity;
import org.devlive.grantforge.service.entity.MenuEntity;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import javax.persistence.*;
import java.util.List;

/**
 * <p> TableRowModel </p>
 * <p> Description : TableRowModel </p>
 * <p> Author : qianmoQ </p>
 * <p> Version : 1.0 </p>
 * <p> Create Time : 2019-05-30 16:11 </p>
 * <p> Author Email: <a href="mailTo:shichengoooo@163.com">qianmoQ</a> </p>
 */
@Data
@ToString
@NoArgsConstructor
@AllArgsConstructor
@Entity
@EntityListeners(value = AuditingEntityListener.class)
@Table(name = "table_row")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class TableRowEntity extends BaseEntity
{

    private String title; // 名称
    private Boolean checked = false; // 选中状态
    private String properties; // 对应数据的字段
    private String type; // 字段类型,后期支持排序等功能
    private Integer sorted; // 排列顺序

    @OneToMany(fetch = FetchType.EAGER)
    @JoinTable(name = "table_row_system_menu_relation",
            joinColumns = @JoinColumn(name = "system_menu_id", referencedColumnName = "id"),
            inverseJoinColumns = @JoinColumn(name = "table_row_id", referencedColumnName = "id"))
    private List<MenuEntity> menus; // 可以使用的菜单列表,只能是菜单使用
}

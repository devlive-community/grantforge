// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.service.entity.icon;

import org.devlive.grantforge.common.support.DateSuooprt;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import org.springframework.format.annotation.DateTimeFormat;

import javax.persistence.*;
import java.util.Date;

/**
 * <p> SystemLogModel </p>
 * <p> Description : SystemLogModel </p>
 * <p> Author : qianmoQ </p>
 * <p> Version : 1.0 </p>
 * <p> Create Time : 2019-01-26 16:03 </p>
 * <p> Author Email: <a href="mailTo:shichengoooo@163.com">qianmoQ</a> </p>
 */
@Data
@ToString
@NoArgsConstructor
@AllArgsConstructor
@Entity
@EntityListeners(value = AuditingEntityListener.class)
@Table(name = "icon")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class IconModel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "name")
    private String name; // 名称

    @Column(name = "code")
    private String code; // 图标代码

    @Column(name = "zh_name")
    private String zhName; // 中文名

    @Column(name = "description")
    private String description;

    @Column(name = "active")
    private Boolean active;

    @Column(name = "create_time")
    @CreatedDate
    @DateTimeFormat(pattern = DateSuooprt.DATE_FORMAT_YYYY_MM_DD_HH_MM_SS)
    private Date createTime;

    @Column(name = "update_time")
    @LastModifiedDate
    @DateTimeFormat(pattern = DateSuooprt.DATE_FORMAT_YYYY_MM_DD_HH_MM_SS)
    private Date updateTime;

    @OneToOne(fetch = FetchType.EAGER)
    @JoinTable(name = "icon_type_icon_relation",
            joinColumns = @JoinColumn(name = "icon_id", referencedColumnName = "id"),
            inverseJoinColumns = @JoinColumn(name = "icon_type_id", referencedColumnName = "id"))
    private IconTypeModel type;

    @OneToOne(fetch = FetchType.EAGER)
    @JoinTable(name = "icon_usage_icon_relation",
            joinColumns = @JoinColumn(name = "icon_id", referencedColumnName = "id"),
            inverseJoinColumns = @JoinColumn(name = "icon_usage_id", referencedColumnName = "id"))
    private IconUsageModel usage;

}

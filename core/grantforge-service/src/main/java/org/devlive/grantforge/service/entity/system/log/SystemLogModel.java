// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.service.entity.system.log;

import org.devlive.grantforge.common.support.DateSuooprt;
import org.devlive.grantforge.service.entity.UserEntity;
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
@Table(name = "system_log")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class SystemLogModel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "remote_ip")
    private String remoteIp; // 访问客户端地址

    @Column(name = "url")
    private String url; // 访问地址

    @Column(name = "method")
    private String method; // 请求方式

    @Column(name = "class")
    private String clazz; // 访问的程序中的哪个类

    @Column(name = "class_method")
    private String classMethod; // 访问的程序中的哪个类的哪个方法

    @Column(name = "args")
    private String args; // 请求参数

    @Column(name = "create_time")
    @CreatedDate
    @DateTimeFormat(pattern = DateSuooprt.DATE_FORMAT_YYYY_MM_DD_HH_MM_SS)
    private Date createTime;

    @Column(name = "update_time")
    @LastModifiedDate
    @DateTimeFormat(pattern = DateSuooprt.DATE_FORMAT_YYYY_MM_DD_HH_MM_SS)
    private Date updateTime;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinTable(name = "system_log_users_relation",
            joinColumns = @JoinColumn(name = "system_log_id", referencedColumnName = "id"),
            inverseJoinColumns = @JoinColumn(name = "users_id", referencedColumnName = "id"))
    private UserEntity user;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinTable(name = "system_log_type_relation",
            joinColumns = @JoinColumn(name = "system_log_id", referencedColumnName = "id"),
            inverseJoinColumns = @JoinColumn(name = "system_log_type_id", referencedColumnName = "id"))
    private SystemLogTypeModel type;

}

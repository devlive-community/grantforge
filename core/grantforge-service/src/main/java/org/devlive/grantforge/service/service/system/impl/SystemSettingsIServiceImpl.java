// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.service.service.system.impl;

import org.devlive.grantforge.common.page.PageModel;
import org.devlive.grantforge.service.repository.system.SystemSettingsRepository;
import org.devlive.grantforge.service.entity.system.SystemSettingsEntity;
import org.devlive.grantforge.service.service.ServiceSupport;
import org.devlive.grantforge.service.service.system.SystemSettingsIService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

/**
 * <p> SystemSettingsServiceImpl </p>
 * <p> Description : SystemSettingsServiceImpl </p>
 * <p> Author : qianmoQ </p>
 * <p> Version : 1.0 </p>
 * <p> Create Time : 2019-05-29 22:35 </p>
 * <p> Author Email: <a href="mailTo:shichengoooo@163.com">qianmoQ</a> </p>
 */
@Service(value = "systemSettingsService")
public class SystemSettingsIServiceImpl implements SystemSettingsIService
{

    @Autowired
    private SystemSettingsRepository repository;

    @Override
    public Long insertModel(Object model) {
        SystemSettingsEntity target = (SystemSettingsEntity) model;
        SystemSettingsEntity temp = this.repository.save(target);
        if (!ObjectUtils.isEmpty(temp)) {
            return temp.getId();
        }
        return ServiceSupport.DEFAULT_ID;
    }

    @Override
    public Object getModelById(Long id) {
        return this.repository.findById(id);
    }

    @Override
    public PageModel getAllByPage(Pageable pageable) {
        Page<SystemSettingsEntity> pageModel = this.repository.findAll(pageable);
        return new PageModel(pageModel.getContent(), pageable, pageModel.getTotalElements());
    }

    @Override
    public long getCount() {
        return this.repository.count();
    }

    @Override
    public SystemSettingsEntity getModelByName(String name) {
        return this.repository.findByName(name);
    }

    @Override
    public SystemSettingsEntity getModelByActiveTrue() {
        return this.repository.findByActiveTrue();
    }

}

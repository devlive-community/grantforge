// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.service.service.system.log.impl;

import org.devlive.grantforge.common.page.PageModel;
import org.devlive.grantforge.service.entity.system.log.SystemLogTypeModel;
import org.devlive.grantforge.service.repository.system.log.SystemLogTypeRepository;
import org.devlive.grantforge.service.service.ServiceSupport;
import org.devlive.grantforge.service.service.system.log.SystemLogTypeIService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

/**
 * <p> SystemLogTypeServiceImpl </p>
 * <p> Description : SystemLogTypeServiceImpl </p>
 * <p> Author : qianmoQ </p>
 * <p> Version : 1.0 </p>
 * <p> Create Time : 2019-05-07 14:31 </p>
 * <p> Author Email: <a href="mailTo:shichengoooo@163.com">qianmoQ</a> </p>
 */
@Service(value = "systemLogTypeService")
public class SystemLogTypeIServiceImpl implements SystemLogTypeIService
{

    @Autowired
    private SystemLogTypeRepository systemLogTypeRepository;

    @Override
    public Long insertModel(Object model) {
        SystemLogTypeModel source = (SystemLogTypeModel) model;
        SystemLogTypeModel user = this.systemLogTypeRepository.save(source);
        if (!ObjectUtils.isEmpty(user)) {
            return user.getId();
        }
        return ServiceSupport.DEFAULT_ID;
    }

    @Override
    public Object getModelById(Long id) {
        return this.systemLogTypeRepository.findById(id);
    }

    @Override
    public PageModel getAllByPage(Pageable pageable) {
        Page<SystemLogTypeModel> pageModel = this.systemLogTypeRepository.findAll(pageable);
        return new PageModel(pageModel.getContent(), pageable, pageModel.getTotalElements());
    }

    @Override
    public long getCount() {
        return this.systemLogTypeRepository.count();
    }

}

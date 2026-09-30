// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.service.service.system.menu;

import org.devlive.grantforge.common.page.PageModel;
import org.devlive.grantforge.service.entity.system.menu.SystemMenuTypeModel;
import org.devlive.grantforge.service.service.ServiceSupport;
import org.devlive.grantforge.service.repository.system.menu.SystemMenuTypeRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

/**
 * <p> SystemMenuTypeServiceImpl </p>
 * <p> Description : SystemMenuTypeServiceImpl </p>
 * <p> Author : qianmoQ </p>
 * <p> Version : 1.0 </p>
 * <p> Create Time : 2019-01-26 15:40 </p>
 * <p> Author Email: <a href="mailTo:shichengoooo@163.com">qianmoQ</a> </p>
 */
@Service(value = "systemMenuTypeService")
public class SystemMenuTypeIServiceImpl implements SystemMenuTypeIService
{

    @Autowired
    private SystemMenuTypeRepository repository;

    @Override
    public Long insertModel(Object model) {
        SystemMenuTypeModel source = (SystemMenuTypeModel) model;
        SystemMenuTypeModel temp = this.repository.save(source);
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
        Page<SystemMenuTypeModel> models = this.repository.findAll(pageable);
        return new PageModel<>(models.getContent(), pageable, models.getTotalElements());
    }

    @Override
    public long getCount() {
        return this.repository.count();
    }

}

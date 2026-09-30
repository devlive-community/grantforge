// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.service.service.icon.impl;

import org.devlive.grantforge.common.page.PageModel;
import org.devlive.grantforge.service.service.ServiceSupport;
import org.devlive.grantforge.service.service.icon.IconIService;
import org.devlive.grantforge.service.entity.icon.IconModel;
import org.devlive.grantforge.service.repository.icon.IconRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

/**
 * <p> IconServiceImpl </p>
 * <p> Description : IconServiceImpl </p>
 * <p> Author : qianmoQ </p>
 * <p> Version : 1.0 </p>
 * <p> Create Time : 2019-05-08 18:59 </p>
 * <p> Author Email: <a href="mailTo:shichengoooo@163.com">qianmoQ</a> </p>
 */
@Service(value = "iconService")
public class IconIServiceImpl implements IconIService
{

    @Autowired
    private IconRepository repository;

    @Override
    public Long insertModel(Object model) {
        IconModel source = (IconModel) model;
        IconModel temp = this.repository.save(source);
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
        Page<IconModel> pageModel = this.repository.findAll(pageable);
        return new PageModel(pageModel.getContent(), pageable, pageModel.getTotalElements());
    }

    @Override
    public long getCount() {
        return this.repository.count();
    }

}

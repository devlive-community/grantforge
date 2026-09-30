// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.service.service.system.log.impl;

import org.devlive.grantforge.common.page.PageModel;
import org.devlive.grantforge.service.entity.system.log.SystemLogModel;
import org.devlive.grantforge.service.repository.system.log.SystemLogRepository;
import org.devlive.grantforge.service.service.ServiceSupport;
import org.devlive.grantforge.service.service.system.log.SystemLogIService;
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
@Service(value = "systemLogService")
public class SystemLogIServiceImpl implements SystemLogIService
{

    @Autowired
    private SystemLogRepository systemLogRepository;

    @Override
    public Long insertModel(SystemLogModel model) {
        SystemLogModel user = this.systemLogRepository.save(model);
        if (!ObjectUtils.isEmpty(user)) {
            return user.getId();
        }
        return ServiceSupport.DEFAULT_ID;
    }

    @Override
    public SystemLogModel getModelById(Long id) {
        return this.systemLogRepository.findById(id).orElseGet(null);
    }

    @Override
    public PageModel getAllByPage(Pageable pageable) {
        Page<SystemLogModel> pageModel = this.systemLogRepository.findAll(pageable);
        return new PageModel(pageModel.getContent(), pageable, pageModel.getTotalElements());
    }

    @Override
    public long getCount() {
        return this.systemLogRepository.count();
    }

}

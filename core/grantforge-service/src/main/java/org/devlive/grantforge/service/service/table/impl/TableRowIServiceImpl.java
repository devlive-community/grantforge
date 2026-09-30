/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.devlive.grantforge.service.service.table.impl;

import org.devlive.grantforge.common.page.PageModel;
import org.devlive.grantforge.service.entity.common.CommonResponseModel;
import org.devlive.grantforge.service.entity.table.TableRowEntity;
import org.devlive.grantforge.service.repository.table.TableRowRepository;
import org.devlive.grantforge.service.service.ServiceSupport;
import org.devlive.grantforge.service.entity.MenuEntity;
import org.devlive.grantforge.service.service.table.TableRowIService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * <p> TableRowServiceImpl </p>
 * <p> Description : TableRowServiceImpl </p>
 * <p> Author : qianmoQ </p>
 * <p> Version : 1.0 </p>
 * <p> Create Time : 2019-05-31 14:38 </p>
 * <p> Author Email: <a href="mailTo:shichengoooo@163.com">qianmoQ</a> </p>
 */
@Service
public class TableRowIServiceImpl implements TableRowIService
{

    @Autowired
    private TableRowRepository repository;

    @Override
    public Long insertModel(Object model) {
        TableRowEntity target = (TableRowEntity) model;
        TableRowEntity temp = this.repository.save(target);
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
        Page<TableRowEntity> pageModel = this.repository.findAll(pageable);
        return new PageModel(pageModel.getContent(), pageable, pageModel.getTotalElements());
    }

    @Override
    public long getCount() {
        return this.repository.count();
    }

    @Override
    public CommonResponseModel getAllByMenus(Pageable pageable, String... menus) {
        List<MenuEntity> models = new ArrayList<>();
        Arrays.asList(menus).forEach(v -> {
            MenuEntity menu = new MenuEntity();
            menu.setId(Long.valueOf(v));
            models.add(menu);
        });
        Page<TableRowEntity> pageModel = this.repository.findAllByMenusIn(models, pageable);
        return CommonResponseModel.success(new PageModel(pageModel.getContent(), pageable, pageModel.getTotalElements()));
    }

}

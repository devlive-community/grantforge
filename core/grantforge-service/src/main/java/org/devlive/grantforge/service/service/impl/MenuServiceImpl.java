// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.service.service.impl;

import org.devlive.grantforge.common.page.PageModel;
import org.devlive.grantforge.service.entity.MenuEntity;
import org.devlive.grantforge.service.entity.system.menu.SystemMenuTypeModel;
import org.devlive.grantforge.service.repository.MenuRepository;
import org.devlive.grantforge.service.service.MenuService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
public class MenuServiceImpl implements MenuService
{
    private final MenuRepository repository;

    public MenuServiceImpl(MenuRepository repository)
    {
        this.repository = repository;
    }

    @Override
    public PageModel<MenuEntity> getAllByParent(Long parent, Pageable pageable)
    {
        Page<MenuEntity> pageModel = this.repository.findAllByParent(parent, pageable);
        return new PageModel(pageModel.getContent(), pageable, pageModel.getTotalElements());
    }

    @Override
    public PageModel<MenuEntity> getAllByParentAndType(Long parent, SystemMenuTypeModel type, Pageable pageable)
    {
        Page<MenuEntity> pageModel = this.repository.findAllByParentAndType(parent, type, pageable);
        return new PageModel(pageModel.getContent(), pageable, pageModel.getTotalElements());
    }

    @Override
    public Iterable<MenuEntity> getByType(SystemMenuTypeModel type)
    {
        return this.repository.findByType(type);
    }

    @Override
    public PageModel<MenuEntity> getByPageAndType(Long type, Pageable pageable)
    {
        SystemMenuTypeModel typeModel = new SystemMenuTypeModel();
        typeModel.setId(type);
        Page<MenuEntity> pageModel = this.repository.findAllByType(typeModel, pageable);
        return new PageModel(pageModel.getContent(), pageable, pageModel.getTotalElements());
    }

    @Override
    public MenuEntity getModeByPath(String path)
    {
        return this.repository.findByUrl(path);
    }

}

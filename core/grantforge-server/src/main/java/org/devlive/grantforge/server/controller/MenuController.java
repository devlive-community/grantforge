// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.controller;

import com.google.common.collect.Lists;
import lombok.extern.slf4j.Slf4j;
import org.devlive.grantforge.common.page.PageModel;
import org.devlive.grantforge.common.pinyin.PinYinUtils;
import org.devlive.grantforge.param.page.PageParam;
import org.devlive.grantforge.param.system.menu.SystemMenuCreateParam;
import org.devlive.grantforge.service.entity.MenuEntity;
import org.devlive.grantforge.service.entity.MethodEntity;
import org.devlive.grantforge.service.entity.common.CommonResponseModel;
import org.devlive.grantforge.service.entity.icon.IconModel;
import org.devlive.grantforge.service.entity.system.menu.SystemMenuTypeModel;
import org.devlive.grantforge.service.repository.MenuRepository;
import org.devlive.grantforge.service.service.MenuService;
import org.devlive.grantforge.server.support.ParamSupport;
import org.springframework.beans.BeanUtils;
import org.springframework.data.domain.Pageable;
import org.springframework.util.ObjectUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping(value = "api/v1/menu")
@Slf4j
public class MenuController
{
    private final MenuRepository repository;
    private final MenuService service;

    protected MenuController(MenuRepository repository, MenuService service)
    {
        this.repository = repository;
        this.service = service;
    }

    @GetMapping
    public CommonResponseModel list(@Validated PageParam configure)
    {
        return service.findAllByPage(repository, ParamSupport.getPageable(configure));
    }

    @PutMapping
    public CommonResponseModel update(@RequestBody MenuEntity configure)
    {
        return service.saveOrUpdate(repository, configure);
    }

    @DeleteMapping
    public CommonResponseModel delete(@RequestParam Long id)
    {
        return service.deleteById(repository, id);
    }

    /**
     * create new menu
     *
     * @param param menu info
     * @return create result
     */
    @PostMapping
    public CommonResponseModel add(@RequestBody @Validated SystemMenuCreateParam param)
    {
        MenuEntity entity = new MenuEntity();
        BeanUtils.copyProperties(param, entity);
        entity.setActive(Boolean.TRUE);
        entity.setCode(PinYinUtils.getFullFirstToUpper(param.getName()));
        // set menu type
        SystemMenuTypeModel systemMenuTypeModel = new SystemMenuTypeModel();
        systemMenuTypeModel.setId(Long.valueOf(param.getType()));
        entity.setType(systemMenuTypeModel);
        List<MethodEntity> methods = Lists.newArrayList();
        param.getMethod().forEach(v -> {
            MethodEntity method = new MethodEntity();
            method.setId(Long.valueOf(v));
            methods.add(method);
        });
        entity.setMethods(methods);
        if (ObjectUtils.isEmpty(param.getParent())) {
            entity.setParent(0L);
        } else {
            entity.setParent(param.getParent());
        }
        IconModel icon = new IconModel();
        icon.setId(Long.valueOf(param.getIconId()));
        entity.setIcon(icon);
        return this.service.saveOrUpdate(repository, entity);
    }

    /**
     * get all model by page
     *
     * @param param page info
     * @return all model by page
     */
    @GetMapping(value = "type")
    public CommonResponseModel list(@Validated PageParam param,
                                    @RequestParam(value = "type") Long type)
    {
        Pageable pageable = PageModel.getPageable(param.getPage(), param.getSize());
        return CommonResponseModel.success(this.service.getByPageAndType(type, pageable));
    }

    /**
     * get all model by parent
     *
     * @param parent parent id
     * @return all model by parent
     */
    @GetMapping(value = "parent")
    public CommonResponseModel getByParent(@Validated PageParam param,
                                           @RequestParam(value = "parent", defaultValue = "0") Long parent,
                                           @RequestParam(value = "type", defaultValue = "0") Long type)
    {
        Pageable pageable = PageModel.getPageable(param.getPage(), param.getSize());
        if (type > 0) {
            SystemMenuTypeModel typeModel = new SystemMenuTypeModel();
            typeModel.setId(type);
            return CommonResponseModel.success(this.service.getAllByParentAndType(parent, typeModel, pageable));
        }
        return CommonResponseModel.success(this.service.getAllByParent(parent, pageable));
    }

    @GetMapping(value = "detail")
    public CommonResponseModel getByPath(@RequestParam(value = "path") String path)
    {
        return CommonResponseModel.success(this.service.getModeByPath(path));
    }
}

// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.controller;

import org.devlive.grantforge.param.page.PageParam;
import org.devlive.grantforge.server.support.ParamSupport;
import org.devlive.grantforge.service.entity.common.CommonResponseModel;
import org.devlive.grantforge.service.service.BaseService;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.PagingAndSortingRepository;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;

import java.io.Serializable;

public abstract class BaseController<T> implements Serializable
{
    private final PagingAndSortingRepository repository;
    private final BaseService<T> service;

    protected BaseController(PagingAndSortingRepository repository, BaseService<T> service)
    {
        this.repository = repository;
        this.service = service;
    }

    /**
     * 根据分页获取数据
     *
     * @param configure 分页配置
     * @return 当前配置数据结果集
     */
    @GetMapping
    public CommonResponseModel list(@Validated PageParam configure)
    {
        Pageable pageable = ParamSupport.getPageable(configure);
        return service.findAllByPage(repository, pageable);
    }

    /**
     * 保存 & 修改
     */
    @RequestMapping(method = {RequestMethod.POST, RequestMethod.PUT})
    public CommonResponseModel saveAndUpdate(@RequestBody T configure)
    {
        return service.saveOrUpdate(repository, configure);
    }

    @DeleteMapping
    public CommonResponseModel delete(@RequestParam(value = "id") Long id)
    {
        return service.deleteById(repository, id);
    }
}

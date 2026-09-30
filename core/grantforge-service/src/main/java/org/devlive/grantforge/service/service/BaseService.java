// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.service.service;

import org.devlive.grantforge.common.page.PageModel;
import org.devlive.grantforge.service.entity.UserEntity;
import org.devlive.grantforge.service.entity.common.CommonResponseModel;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.PagingAndSortingRepository;

import java.util.Optional;

public interface BaseService<T>
{
    /**
     * 根据分页获取数据
     *
     * @param pageable 分页配置
     * @return 当前配置数据结果集
     */
    default CommonResponseModel<T> findAllByPage(PagingAndSortingRepository repository, Pageable pageable)
    {
        Page<UserEntity> pageModel = repository.findAll(pageable);
        return CommonResponseModel.success(new PageModel(pageModel.getContent(), pageable, pageModel.getTotalElements()));
    }

    /**
     * 保存 & 修改数据
     *
     * @param repository 用于操作处理数据的执行器
     * @param configure  需要处理的实际数据
     * @return 最终处理结果
     */
    default CommonResponseModel<T> saveOrUpdate(PagingAndSortingRepository repository, T configure)
    {
        return CommonResponseModel.success(repository.save(configure));
    }

    /**
     * 删除指定数据
     *
     * @param repository 用于操作处理数据的执行器
     * @param id         需要删除数据的唯一标记
     * @return 返回指定的数据标记
     */
    default CommonResponseModel<T> deleteById(PagingAndSortingRepository repository, Long id)
    {
        repository.deleteById(id);
        return CommonResponseModel.success(id);
    }

    default CommonResponseModel<T> findById(PagingAndSortingRepository repository, Long id)
    {
        Optional<T> optional = repository.findById(id);
        return CommonResponseModel.success(optional.get());
    }
}

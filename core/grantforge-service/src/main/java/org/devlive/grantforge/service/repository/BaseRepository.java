package org.devlive.grantforge.service.repository;

import org.springframework.data.repository.NoRepositoryBean;
import org.springframework.data.repository.PagingAndSortingRepository;

import java.io.Serializable;

/**
 * <p> BaseRepository </p>
 * <p> Description : BaseRepository </p>
 * <p> Author : qianmoQ </p>
 * <p> Version : 1.0 </p>
 * <p> Create Time : 2019-05-25 21:00 </p>
 * <p> Author Eamil: <a href="mailTo:shichengoooo@163.com">qianmoQ</a> </p>
 */
@NoRepositoryBean
public interface BaseRepository<BaseModel, ID extends Serializable> extends PagingAndSortingRepository<BaseModel, ID> {

    /**
     * 根据名称查询数据
     *
     * @param name 名称
     * @return 当前名称对应的数据
     */
    BaseModel findByName(String name);

    /**
     * 获取当前激活的数据
     *
     * @return 当前激活的数据
     */
    BaseModel findByActiveTrue();

}

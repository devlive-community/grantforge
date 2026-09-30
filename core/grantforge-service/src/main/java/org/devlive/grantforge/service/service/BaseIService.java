// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.service.service;
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

import org.devlive.grantforge.common.page.PageModel;
import org.springframework.data.domain.Pageable;

/**
 * <p> BaseService </p>
 * <p> Description : BaseService </p>
 * <p> Author : qianmoQ </p>
 * <p> Version : 1.0 </p>
 * <p> Create Time : 2019-02-12 14:38 </p>
 * <p> Author Eamil: <a href="mailTo:shichengoooo@163.com">qianmoQ</a> </p>
 */
public interface BaseIService<T> {

    /**
     * add model
     *
     * @param model model info
     * @return insert count
     */
    Long insertModel(T model);

    /**
     * get model by id
     *
     * @param id model id
     * @return model
     */
    T getModelById(Long id);

    /**
     * get all model by page
     *
     * @param pageable page info
     * @return all model for page
     */
    PageModel<T> getAllByPage(Pageable pageable);

    /**
     * 获取数据总数
     *
     * @return 数据总数
     */
    long getCount();

}

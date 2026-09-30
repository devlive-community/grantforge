// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.service.service.system.role; /**
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

import org.devlive.grantforge.service.entity.system.menu.SystemMenuTypeModel;
import org.devlive.grantforge.service.entity.RoleEntity;
import org.devlive.grantforge.service.entity.tree.TreeModel;

import java.util.List;

/**
 * <p> SystemRoleSeniorService </p>
 * <p> Description : SystemRoleSeniorService </p>
 * <p> Author : qianmoQ </p>
 * <p> Version : 1.0 </p>
 * <p> Create Time : 2019-04-29 13:54 </p>
 * <p> Author Eamil: <a href="mailTo:shichengoooo@163.com">qianmoQ</a> </p>
 */
public interface SystemRoleSeniorService {

    /**
     * Query the menu by the permission ID
     *
     * @param id
     * @param flag
     * @return
     */
    List<TreeModel> findTreeMenuById(RoleEntity roleModel, SystemMenuTypeModel typeModel);

    List<TreeModel> findMenuById(Long id);

    List<TreeModel> findMenuByIds(List<RoleEntity> roles);

}

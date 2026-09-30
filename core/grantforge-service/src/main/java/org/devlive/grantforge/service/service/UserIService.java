// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.service.service;

import org.devlive.grantforge.service.entity.UserEntity;

public interface UserIService extends BaseIService
{

    /**
     * get model by username and password
     *
     * @param name     username
     * @param password password
     * @return user model
     */
    UserEntity getModelByNameAndPassword(String name, String password);

    /**
     * get model by username
     *
     * @param name username
     * @return user model
     */
    UserEntity getModelByName(String name);

    UserEntity getDistinctById(Long id);

    long deleteById(Long id);
}

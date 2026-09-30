// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.service.service;

import org.devlive.grantforge.service.entity.MethodEntity;

public interface MethodService extends BaseService<MethodEntity>
{
    MethodEntity getByMethod(String method);
}

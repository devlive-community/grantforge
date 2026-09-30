// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.service.repository;

import org.devlive.grantforge.service.entity.MethodEntity;
import org.springframework.data.repository.PagingAndSortingRepository;

public interface MethodRepository extends PagingAndSortingRepository<MethodEntity, Long>
{
    MethodEntity findByMethod(String method);
}

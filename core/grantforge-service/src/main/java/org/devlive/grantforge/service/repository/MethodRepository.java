package org.devlive.grantforge.service.repository;

import org.devlive.grantforge.service.entity.MethodEntity;
import org.springframework.data.repository.PagingAndSortingRepository;

public interface MethodRepository extends PagingAndSortingRepository<MethodEntity, Long>
{
    MethodEntity findByMethod(String method);
}

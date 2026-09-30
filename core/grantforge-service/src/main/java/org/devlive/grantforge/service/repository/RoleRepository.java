package org.devlive.grantforge.service.repository;

import org.devlive.grantforge.service.entity.RoleEntity;
import org.springframework.data.repository.PagingAndSortingRepository;

public interface RoleRepository extends PagingAndSortingRepository<RoleEntity, Long>
{
    RoleEntity findByName(String name);
}

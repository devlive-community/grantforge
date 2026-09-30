package org.devlive.grantforge.service.service;

import org.devlive.grantforge.common.page.PageModel;
import org.devlive.grantforge.service.entity.RoleEntity;
import org.springframework.data.domain.Pageable;

public interface RoleService
    extends BaseService<RoleEntity>
{
    /**
     * get model by id
     *
     * @param id id
     * @return model response id
     */
    RoleEntity getModelById(Long id);

    /**
     * get model by name
     *
     * @param name name
     * @return model response by name
     */
    RoleEntity getModelByName(String name);
}

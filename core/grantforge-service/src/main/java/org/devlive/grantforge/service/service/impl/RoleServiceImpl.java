package org.devlive.grantforge.service.service.impl;

import org.devlive.grantforge.service.entity.RoleEntity;
import org.devlive.grantforge.service.entity.common.CommonResponseModel;
import org.devlive.grantforge.service.repository.RoleRepository;
import org.devlive.grantforge.service.service.RoleService;
import org.springframework.data.repository.PagingAndSortingRepository;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import java.util.Optional;

@Service
public class RoleServiceImpl implements RoleService
{
    private final RoleRepository repository;

    public RoleServiceImpl(RoleRepository repository)
    {
        this.repository = repository;
    }

    @Override
    public CommonResponseModel<RoleEntity> saveOrUpdate(PagingAndSortingRepository repository, RoleEntity configure)
    {
        // 如果是更新数据，需要将原有菜单数据重新构建并组装，防止数据丢失
        if (!ObjectUtils.isEmpty(configure.getId())) {
            Optional<RoleEntity> optional = repository.findById(configure.getId());
            if (optional.isPresent()) {
                configure.setMenus(optional.get().getMenus());
            }
        }
        return RoleService.super.saveOrUpdate(repository, configure);
    }

    @Override
    public RoleEntity getModelById(Long id)
    {
        Optional<RoleEntity> model = this.repository.findById(id);
        if (model.isPresent()) {
            return model.get();
        }
        return null;
    }

    @Override
    public RoleEntity getModelByName(String name)
    {
        return this.repository.findByName(name);
    }
}

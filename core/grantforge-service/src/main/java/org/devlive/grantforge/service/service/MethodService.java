package org.devlive.grantforge.service.service;

import org.devlive.grantforge.service.entity.MethodEntity;

public interface MethodService extends BaseService<MethodEntity>
{
    MethodEntity getByMethod(String method);
}

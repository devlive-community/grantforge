package org.devlive.grantforge.service.service.impl;

import org.devlive.grantforge.common.page.PageModel;
import org.devlive.grantforge.service.entity.UserEntity;
import org.devlive.grantforge.service.repository.user.UserRepository;
import org.devlive.grantforge.service.service.ServiceSupport;
import org.devlive.grantforge.service.service.UserIService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import java.util.Optional;

@Service
public class UserIServiceImpl implements UserIService
{
    private final UserRepository repository;

    public UserIServiceImpl(UserRepository repository)
    {
        this.repository = repository;
    }

    @Override
    public Long insertModel(Object model)
    {
        UserEntity source = (UserEntity) model;
        UserEntity user = this.repository.save(source);
        if (!ObjectUtils.isEmpty(user)) {
            return user.getId();
        }
        return ServiceSupport.DEFAULT_ID;
    }

    @Override
    public UserEntity getModelById(Long id)
    {
        Optional<UserEntity> model = this.repository.findById(id);
        if (model.isPresent()) {
            return model.get();
        }
        return null;
    }

    @Override
    public PageModel getAllByPage(Pageable pageable)
    {
        Page<UserEntity> pageModel = this.repository.findAllByIsSystemIsFalse(pageable);
        return new PageModel(pageModel.getContent(), pageable, pageModel.getTotalElements());
    }

    @Override
    public UserEntity getModelByNameAndPassword(String name, String password)
    {
        return this.repository.findByNameAndPassword(name, password);
    }

    @Override
    public UserEntity getModelByName(String name)
    {
        return this.repository.findByName(name);
    }

    @Override
    public UserEntity getDistinctById(Long id)
    {
        return this.repository.findDistinctById(id);
    }

    @Override
    public long deleteById(Long id)
    {
        repository.deleteById(id);
        return id;
    }

    @Override
    public long getCount()
    {
        return this.repository.count();
    }

}

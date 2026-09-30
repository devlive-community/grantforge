package org.devlive.grantforge.service.repository.user;

import org.devlive.grantforge.service.entity.UserEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.PagingAndSortingRepository;

public interface UserRepository extends PagingAndSortingRepository<UserEntity, Long> {

    /**
     * find model by username and password
     *
     * @param name     username
     * @param password password
     * @return user model
     */
    UserEntity findByNameAndPassword(String name, String password);

    /**
     * find model by username
     *
     * @param name username
     * @return user model
     */
    UserEntity findByName(String name);

    UserEntity findDistinctById(Long id);

    /**
     * 查询所有的非系统用户信息
     *
     * @param pageable 分页查询信息
     * @return 所有的非系统用户信息
     */
    Page<UserEntity> findAllByIsSystemIsFalse(Pageable pageable);

}

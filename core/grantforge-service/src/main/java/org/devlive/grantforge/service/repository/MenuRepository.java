package org.devlive.grantforge.service.repository;

import org.devlive.grantforge.service.entity.MenuEntity;
import org.devlive.grantforge.service.entity.MethodEntity;
import org.devlive.grantforge.service.entity.system.menu.SystemMenuTypeModel;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.PagingAndSortingRepository;

import java.util.List;

public interface MenuRepository extends PagingAndSortingRepository<MenuEntity, Long>
{

    Page<MenuEntity> findAllByActiveTrue(Pageable pageable);

    Page<MenuEntity> findAllByActiveTrueAndType(SystemMenuTypeModel model, Pageable pageable);

    /**
     * find all model by parent
     *
     * @param parent parent id
     * @return all model from parent
     */
    Page<MenuEntity> findAllByParent(Long parent, Pageable pageable);

    /**
     * 根据菜单父节点,菜单类型查询所有菜单数据并进行分页
     *
     * @param parent   菜单父节点
     * @param type     菜单类型
     * @param pageable 分页信息
     * @return 返回的数据
     */
    Page<MenuEntity> findAllByParentAndType(Long parent, SystemMenuTypeModel type, Pageable pageable);

    /**
     * find model by type
     *
     * @param type type info
     * @return model from type
     */
    Iterable<MenuEntity> findByType(SystemMenuTypeModel type);

    Page<MenuEntity> findAllByType(SystemMenuTypeModel type, Pageable pageable);

    /**
     * 根据url获取详情
     *
     * @param url url地址
     * @return 详情
     */
    MenuEntity findByUrl(String url);

    List<MenuEntity> findAllByIsSystemIsTrue();

    MenuEntity findByUrlAndMethodsContaining(String url, MethodEntity method);
}

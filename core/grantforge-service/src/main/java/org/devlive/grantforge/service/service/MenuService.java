package org.devlive.grantforge.service.service;

import org.devlive.grantforge.common.page.PageModel;
import org.devlive.grantforge.service.entity.MenuEntity;
import org.devlive.grantforge.service.entity.system.menu.SystemMenuTypeModel;
import org.springframework.data.domain.Pageable;

public interface MenuService extends BaseService<MenuEntity>
{

    /**
     * get all model by parent
     *
     * @param parent parent id
     * @return all model from parent
     */
    PageModel<MenuEntity> getAllByParent(Long parent, Pageable pageable);

    /**
     * 根据菜单父节点,菜单类型查询所有菜单数据并进行分页
     *
     * @param parent   菜单父节点
     * @param type     菜单类型
     * @param pageable 分页信息
     * @return 返回的数据
     */
    PageModel<MenuEntity> getAllByParentAndType(Long parent, SystemMenuTypeModel type, Pageable pageable);

    /**
     * find model by type
     *
     * @param type type info
     * @return model from type
     */
    Iterable<MenuEntity> getByType(SystemMenuTypeModel type);

    PageModel<MenuEntity> getByPageAndType(Long type, Pageable pageable);

    /**
     * 根据路径获取菜单详情
     *
     * @param path 路径
     * @return 菜单详情
     */
    MenuEntity getModeByPath(String path);
}

// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.service.service.system.role;

import org.devlive.grantforge.service.entity.MenuEntity;
import org.devlive.grantforge.service.entity.tree.TreeItemModel;
import org.devlive.grantforge.service.entity.tree.TreeModel;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class PermissionTreeBuilder
{
    private PermissionTreeBuilder() {}

    static List<TreeModel> build(List<MenuEntity> menus, Set<Long> selected)
    {
        Map<Long, MenuEntity> entities = new LinkedHashMap<>();
        menus.stream().filter(menu -> menu.getId() != null)
                .sorted(Comparator.comparing(MenuEntity::getSorted, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(MenuEntity::getId))
                .forEach(menu -> entities.putIfAbsent(menu.getId(), menu));
        Map<Long, TreeModel> nodes = new LinkedHashMap<>();
        for (MenuEntity menu : entities.values()) {
            TreeModel node = new TreeModel();
            node.setId(menu.getId());
            node.setTitle(menu.getName());
            node.setUrl(menu.getUrl());
            node.setSorted(menu.getSorted());
            node.setCode(menu.getCode());
            node.setTips(menu.getTips());
            node.setNewd(menu.getNewd());
            node.setChecked(selected.contains(menu.getId()));
            node.setSelected(node.getChecked());
            node.setChildren(new ArrayList<>());
            node.setIcon(menu.getIcon() == null ? "" : menu.getIcon().getCode());
            TreeItemModel item = TreeItemModel.buildNew();
            item.setPhrase(menu.getId());
            node.setItem(item);
            nodes.put(menu.getId(), node);
        }
        List<TreeModel> roots = new ArrayList<>();
        for (MenuEntity menu : entities.values()) {
            TreeModel node = nodes.get(menu.getId());
            Long parent = menu.getParent();
            if (parent == null || parent == 0 || !nodes.containsKey(parent) || cycle(menu.getId(), parent, entities)) {
                roots.add(node);
            }
            else {
                nodes.get(parent).getChildren().add(node);
            }
        }
        return roots;
    }

    private static boolean cycle(Long id, Long parent, Map<Long, MenuEntity> entities)
    {
        Set<Long> visited = new HashSet<>();
        while (parent != null && parent != 0 && entities.containsKey(parent)) {
            if (id.equals(parent) || !visited.add(parent)) {
                return true;
            }
            parent = entities.get(parent).getParent();
        }
        return false;
    }
}

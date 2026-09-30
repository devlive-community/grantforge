package org.devlive.grantforge.service.service.system.role;

import org.devlive.grantforge.service.entity.MenuEntity;
import org.devlive.grantforge.service.entity.tree.TreeModel;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class PermissionTreeBuilderTest
{
    @Test
    public void nullAndMissingParentsRemainVisible()
    {
        List<TreeModel> tree = PermissionTreeBuilder.build(Arrays.asList(menu(1, null, 2), menu(2, 999L, 1)), Collections.emptySet());
        assertEquals(Long.valueOf(2), tree.get(0).getId());
        assertEquals(2, tree.size());
    }

    @Test
    public void nestedMenusRetainSelectionAndIdentity()
    {
        List<TreeModel> tree = PermissionTreeBuilder.build(Arrays.asList(menu(3, 2L, 1), menu(1, 0L, 1), menu(2, 1L, 1)), Collections.singleton(3L));
        TreeModel leaf = tree.get(0).getChildren().get(0).getChildren().get(0);
        assertEquals(Long.valueOf(3), leaf.getId());
        assertTrue(leaf.getChecked());
        assertEquals(Long.valueOf(3), leaf.getItem().getPhrase());
    }

    @Test
    public void cyclicParentsDoNotCreateCyclicJsonGraphs()
    {
        List<TreeModel> tree = PermissionTreeBuilder.build(Arrays.asList(menu(1, 2L, 1), menu(2, 1L, 1)), Collections.emptySet());
        assertEquals(2, tree.size());
        assertTrue(tree.stream().allMatch(node -> node.getChildren().isEmpty()));
    }

    private MenuEntity menu(long id, Long parent, int sorted)
    {
        MenuEntity menu = new MenuEntity();
        menu.setId(id);
        menu.setParent(parent);
        menu.setSorted(sorted);
        menu.setName("menu " + id);
        return menu;
    }
}

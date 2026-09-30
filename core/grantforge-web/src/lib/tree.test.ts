import { describe, expect, it } from 'vitest'
import { checkedIds, flattenTree, menuPaths, toggleTree } from './tree'
import type { MenuTree } from '@/types/api'
const tree: MenuTree[] = [{ id: 1, title: 'group', url: '#', checked: true, children: [{ id: 2, title: 'users', url: '/admin/users', checked: true }, { id: 3, title: 'roles', url: '/admin/roles' }] }]
describe('permission trees', () => {
  it('preserves nested existing assignments', () => expect(checkedIds(tree)).toEqual([1, 2]))
  it('selects ancestors when granting a leaf', () => expect(toggleTree(tree, [], 2, true).sort()).toEqual([1, 2]))
  it('removes every descendant when clearing a group', () => expect(toggleTree(tree, [1, 2, 3], 1, false)).toEqual([]))
  it('does not interpret grouping placeholders as navigation', () => expect([...menuPaths(tree)]).toEqual(['/admin/users', '/admin/roles']))
  it('avoids duplicate or cyclic nodes during flattening', () => {
    const node: MenuTree = { id: 1, title: 'cycle' }; node.children = [node]
    expect(flattenTree([node])).toHaveLength(1)
  })
})

import type { MenuTree } from '@/types/api'

export function flattenTree(nodes: MenuTree[]): MenuTree[] {
  const result: MenuTree[] = []
  const seen = new Set<number>()
  function visit(items: MenuTree[]) {
    for (const item of items) {
      if (seen.has(item.id)) continue
      seen.add(item.id); result.push(item)
      if (item.children) visit(item.children)
    }
  }
  visit(nodes)
  return result
}
export function checkedIds(nodes: MenuTree[]): number[] { return flattenTree(nodes).filter(node => node.checked).map(node => node.id) }
export function menuPaths(nodes: MenuTree[]): Set<string> { return new Set(flattenTree(nodes).flatMap(node => node.url && node.url !== '#' ? [node.url] : [])) }
export function toggleTree(nodes: MenuTree[], selected: number[], id: number, checked: boolean): number[] {
  const result = new Set(selected)
  function visit(items: MenuTree[], ancestors: number[]): boolean {
    for (const item of items) {
      if (item.id === id) {
        for (const node of flattenTree([item])) if (checked) result.add(node.id); else result.delete(node.id)
        if (checked) for (const ancestor of ancestors) result.add(ancestor)
        return true
      }
      if (item.children && visit(item.children, [...ancestors, item.id])) return true
    }
    return false
  }
  visit(nodes, [])
  return [...result]
}

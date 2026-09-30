export interface Entity {
  id: number
  name: string
  active?: boolean
  createTime?: string
  updateTime?: string
}
export interface Role extends Entity { code?: string; description?: string }
export interface User extends Entity { avatar?: string; email?: string; locked?: boolean; isSystem?: boolean; roles?: Role[] }
export interface Method extends Entity { code?: string; method: string; description?: string; isSystem?: boolean }
export interface NamedOption extends Entity { code?: string; description?: string }
export interface Menu extends Entity {
  code?: string; url: string; parent?: number; sorted?: number; level?: number; tips?: string
  description?: string; newd?: boolean; isSystem?: boolean
  type?: NamedOption; icon?: NamedOption; methods?: Method[]
}
export interface MenuTree { id: number; title: string; url?: string; checked?: boolean; children?: MenuTree[] }
export interface Page<T> { content: T[]; number: number; size: number; totalElements: number; totalPages: number }
export interface ApiResponse<T> { code: number; message: string; data: T }
export interface OverviewItem { title: string; value: number; color?: string }
export interface TableColumn { key: string; label: string; class?: string }

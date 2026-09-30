import { TableColumnEntity } from '@/entity/TableEntity'

const headers: Array<TableColumnEntity> = [
  new TableColumnEntity('#', 'id'),
  new TableColumnEntity('名称', 'name'),
  new TableColumnEntity('头像', 'avatar', 'avatar'),
  new TableColumnEntity('邮箱', 'email'),
  new TableColumnEntity('激活状态', 'active', 'active'),
  new TableColumnEntity('权限', 'permission', 'permission'),
  new TableColumnEntity('创建时间', 'createTime', undefined, true),
  new TableColumnEntity('修改时间', 'updateTime', undefined, true),
  new TableColumnEntity('操作', 'action', 'action')
]

export default {
  headers: headers
}

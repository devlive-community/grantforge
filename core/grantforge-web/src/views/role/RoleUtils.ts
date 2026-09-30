import { TableColumnEntity } from '@/entity/TableEntity'

const headers: Array<TableColumnEntity> = [
  new TableColumnEntity('#', 'id'),
  new TableColumnEntity('名称', 'name'),
  new TableColumnEntity('编码', 'code'),
  new TableColumnEntity('描述', 'description', undefined, true),
  new TableColumnEntity('激活状态', 'active', 'active'),
  new TableColumnEntity('创建时间', 'createTime', undefined, true),
  new TableColumnEntity('修改时间', 'updateTime', undefined, true),
  new TableColumnEntity('操作', 'action', 'action')
]

export default {
  headers: headers
}

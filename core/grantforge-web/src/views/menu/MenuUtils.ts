import { TableColumnEntity } from '@/entity/TableEntity'

const headers: Array<TableColumnEntity> = [
  new TableColumnEntity('#', 'id', undefined, undefined, undefined, 100),
  new TableColumnEntity('名称', 'name', undefined, true, undefined, 100),
  new TableColumnEntity('编码', 'code', undefined, true, undefined, 100),
  new TableColumnEntity('描述', 'description', undefined, true, undefined, 100),
  new TableColumnEntity('等级', 'level', undefined, undefined, undefined, 100),
  new TableColumnEntity('请求方式', 'methods', 'method', undefined, undefined, 100),
  new TableColumnEntity('排序', 'sorted', undefined, undefined, undefined, 100),
  new TableColumnEntity('新功能', 'newd', 'newd', undefined, undefined, 100),
  new TableColumnEntity('提示', 'tips', undefined, true, undefined, 100),
  new TableColumnEntity('类型', 'type', 'type', true, undefined, 100),
  new TableColumnEntity('URL', 'url', undefined, true, undefined, 100),
  new TableColumnEntity('激活状态', 'active', 'active', undefined, undefined, 100),
  new TableColumnEntity('创建时间', 'createTime', undefined, true, undefined, 100),
  new TableColumnEntity('修改时间', 'updateTime', undefined, true, undefined, 100)
]

export default {
  headers: headers
}

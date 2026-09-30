import { BaseService } from '@/services/BaseService'
import { ResponseEntity } from '@/entity/ResponseEntity'
import HttpUtils from '@/utils/http'

const baseRoleUrl = '/api/v1/role'
const baseUrl = '/api/v1/menu'

export class MenuService extends BaseService<ResponseEntity> {
  constructor () {
    super(baseUrl)
  }

  getMenusByUser (id: number): Promise<ResponseEntity> {
    return HttpUtils.get(`${baseRoleUrl}/menu?id=${id}`)
  }
}

export default new MenuService()

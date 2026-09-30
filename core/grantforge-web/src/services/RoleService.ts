import { BaseService } from '@/services/BaseService'
import { ResponseEntity } from '@/entity/ResponseEntity'
import HttpUtils from '@/utils/http'
import { AssignMenuEntity } from '@/entity/RoleEntity'

const baseUrl = '/api/v1/role'

export class RoleService extends BaseService<ResponseEntity> {
  constructor () {
    super(baseUrl)
  }

  getMenusByRole (id: number): Promise<ResponseEntity> {
    return HttpUtils.get(`${baseUrl}/menus?id=${id}`)
  }

  assignMenu (configure: AssignMenuEntity): Promise<ResponseEntity> {
    return HttpUtils.put(`${baseUrl}/menus`, configure)
  }
}

export default new RoleService()

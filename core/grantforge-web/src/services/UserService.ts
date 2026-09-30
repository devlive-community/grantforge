import { BaseService } from '@/services/BaseService'
import { ResponseEntity } from '@/entity/ResponseEntity'
import HttpUtils from '@/utils/http'
import { PageEntity } from '@/entity/PageEntity'
import { AssignRoleEntity } from '@/entity/RoleEntity'
import { UserEntity } from '@/entity/UserEntity'

const baseUrl = '/api/v1/user'

export class UserService extends BaseService<ResponseEntity> {
  constructor () {
    super(baseUrl)
  }

  saveOrUpdate<T> (configure: T): Promise<ResponseEntity> {
    return HttpUtils.post(`${baseUrl}/register`, configure)
  }

  getAllByPage<T> (page: PageEntity): Promise<ResponseEntity> {
    return HttpUtils.get(`${baseUrl}`, page)
  }

  getInfoByUserName<T> (username: string): Promise<ResponseEntity> {
    return HttpUtils.get(`${baseUrl}/info/${username}`)
  }

  assignRole (configure: AssignRoleEntity): Promise<ResponseEntity> {
    return HttpUtils.put(`${baseUrl}/role`, configure)
  }

  // @ts-ignore
  delete (configure: UserEntity): Promise<ResponseEntity> {
    return HttpUtils.delete(`${baseUrl}?id=${configure.id}`)
  }
}

export default new UserService()

import { BaseEntity } from '@/entity/BaseEntity'
import { RoleEntity } from '@/entity/RoleEntity'

export class UserEntity extends BaseEntity {
  username = ''
  password = ''
  roles: Array<RoleEntity> | undefined
  token?: string | undefined
  type?: string | undefined
}

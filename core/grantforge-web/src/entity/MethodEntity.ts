import { BaseEntity } from '@/entity/BaseEntity'

export class MethodEntity extends BaseEntity {
  method: string | undefined
  system: boolean | undefined
}

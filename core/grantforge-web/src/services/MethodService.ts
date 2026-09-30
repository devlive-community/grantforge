import { BaseService } from '@/services/BaseService'
import { ResponseEntity } from '@/entity/ResponseEntity'

const baseUrl = '/api/v1/method'

export class MethodService extends BaseService<ResponseEntity> {
  constructor () {
    super(baseUrl)
  }
}

export default new MethodService()

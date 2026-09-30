import HttpUtils from '@/utils/http'
import { ResponseEntity } from '@/entity/ResponseEntity'
import { PageEntity } from '@/entity/PageEntity'

export abstract class BaseService<T> {
  private readonly baseUrl: string;

  protected constructor (baseUrl: string) {
    this.baseUrl = baseUrl
  }

  saveOrUpdate<T> (configure: T): Promise<ResponseEntity> {
    // @ts-ignore
    if (configure.id) {
      return HttpUtils.put(this.baseUrl, configure)
    } else {
      return HttpUtils.post(this.baseUrl, configure)
    }
  }

  delete (id: number): Promise<ResponseEntity> {
    return HttpUtils.delete(`${this.baseUrl}?id=${id}`)
  }

  getAllByPage<T> (page: PageEntity): Promise<ResponseEntity> {
    return HttpUtils.get(this.baseUrl, page)
  }
}

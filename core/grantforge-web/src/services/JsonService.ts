import HttpUtils from '@/utils/http'

const baseUrl = 'api/v1/json'

class JsonService {
  doPretty (json: string): Promise<any> {
    return HttpUtils.post(`${baseUrl}/pretty`, json)
  }

  doCompression (json: string): Promise<any> {
    return HttpUtils.post(`${baseUrl}/compression`, json)
  }
}

export default new JsonService()

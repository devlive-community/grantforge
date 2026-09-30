import HttpUtils from '@/utils/http'
import { TOKEN } from '@/utils/support'

const baseUrl = '/oauth/token'

class AuthService
{
  doAuth(configure: any): Promise<any>
  {
    return HttpUtils.doAuth(baseUrl, configure.username, configure.password)
  }

  saveAuth(token: string)
  {
    localStorage.setItem(TOKEN, token)
  }

  logout()
  {
    localStorage.removeItem(TOKEN)
  }

  getAuth(): string
  {
    return localStorage.getItem(TOKEN) as string
  }

  getAuthUserName(): string
  {
    return localStorage.getItem(TOKEN) as string
  }
}

export default new AuthService()

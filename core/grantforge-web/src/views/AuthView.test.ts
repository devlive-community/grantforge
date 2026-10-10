// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

import { flushPromises } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { setLocale } from '@/i18n'
import { useBootstrap } from '@/stores/bootstrap'
import { mountView } from '../../tests/unit/mountView'

const api = vi.hoisted(() => ({ request: vi.fn() }))
vi.mock('@/lib/api', async importOriginal => ({ ...await importOriginal<typeof import('@/lib/api')>(), ...api }))
const navigation = vi.hoisted(() => ({ continueAuthorization: vi.fn() }))
vi.mock('@/lib/authorize', async importOriginal => ({ ...await importOriginal<typeof import('@/lib/authorize')>(), ...navigation }))

const { default: AuthView } = await import('./AuthView.vue')

async function submit(wrapper: Awaited<ReturnType<typeof mountView>>['wrapper'], values: Record<string, string>) {
  const inputs = wrapper.findAll('input')
  if (values.name !== undefined) await inputs[0]?.setValue(values.name)
  if (values.password !== undefined) await inputs[1]?.setValue(values.password)
  if (values.confirmation !== undefined) await inputs[2]?.setValue(values.confirmation)
  await wrapper.get('form').trigger('submit')
  await flushPromises()
}

describe('auth view', () => {
  beforeEach(() => {
    localStorage.clear()
    api.request.mockReset()
  })

  it('focuses the username and validates the login form', async () => {
    const { wrapper } = await mountView(AuthView, { props: { mode: 'login' } }, '/auth/login')
    expect(document.activeElement).toBe(wrapper.findAll('input')[0]?.element)
    await submit(wrapper, {})
    expect(wrapper.get('[role="alert"]').text()).toBe('请输入用户名')
    await submit(wrapper, { name: 'admin' })
    expect(wrapper.get('[role="alert"]').text()).toBe('请输入密码')
    wrapper.unmount()
  })

  it('logs in and returns to a same-site redirect only', async () => {
    api.request.mockImplementation((path: string) => Promise.resolve(path === '/api/v1/auth/login'
      ? { username: 'admin', tenantCode: 'default', tenantName: 'Default', systemAccount: true, passwordChangeRequired: false } : []))
    const { wrapper, router } = await mountView(AuthView, { props: { mode: 'login' } }, '/auth/login?redirect=/admin/users')
    await submit(wrapper, { name: ' admin ', password: 'secret' })
    expect(api.request).toHaveBeenCalledWith('/api/v1/auth/login',
      { method: 'POST', anonymous: true, body: { username: 'admin', password: 'secret' } })
    expect(router.currentRoute.value.path).toBe('/admin/users')
    wrapper.unmount()

    const other = await mountView(AuthView, { props: { mode: 'login' } }, '/auth/login?redirect=//evil.example')
    await submit(other.wrapper, { name: 'admin', password: 'secret' })
    expect(other.router.currentRoute.value.path).toBe('/dashboard')
    other.wrapper.unmount()
  })

  it('continues an application\'s sign-in at the authorization endpoint only', async () => {
    navigation.continueAuthorization.mockReset()
    api.request.mockImplementation((path: string) => Promise.resolve(path === '/api/v1/auth/login'
      ? { username: 'admin', tenantCode: 'default', tenantName: 'Default', systemAccount: true, passwordChangeRequired: false } : []))
    const authorize = encodeURIComponent('/oauth2/authorize?response_type=code&client_id=gf_a')
    const { wrapper } = await mountView(AuthView, { props: { mode: 'login' } }, `/auth/login?authorize=${authorize}`)
    await submit(wrapper, { name: 'admin', password: 'secret' })
    expect(navigation.continueAuthorization).toHaveBeenCalledWith('/oauth2/authorize?response_type=code&client_id=gf_a')
    wrapper.unmount()

    const other = await mountView(AuthView, { props: { mode: 'login' } }, `/auth/login?authorize=${encodeURIComponent('https://evil.example/oauth2/authorize?x')}`)
    await submit(other.wrapper, { name: 'admin', password: 'secret' })
    expect(navigation.continueAuthorization).toHaveBeenCalledTimes(1)
    expect(other.router.currentRoute.value.path).toBe('/dashboard')
    other.wrapper.unmount()
  })

  it('asks accounts with two-step sign-in for a code after the password', async () => {
    const { ApiError } = await import('@/lib/api')
    const signedIn = { username: 'admin', tenantCode: 'default', tenantName: 'Default', systemAccount: true, passwordChangeRequired: false }
    api.request.mockImplementation((path: string, options?: { body?: { code?: string } }) => {
      if (path === '/api/v1/auth/login') return Promise.reject(new ApiError('请输入验证码', 401, 0, null, { status: 401, code: 'GF-IDENTITY-105' }))
      if (path === '/api/v1/auth/mfa' && options?.body?.code === '000000') {
        return Promise.reject(new ApiError('验证码错误', 400, 0, null, { status: 400, code: 'GF-IDENTITY-100' }))
      }
      if (path === '/api/v1/auth/mfa' && options?.body?.code === 'late') {
        return Promise.reject(new ApiError('登录超时', 401, 0, null, { status: 401, code: 'GF-IDENTITY-104' }))
      }
      return Promise.resolve(path === '/api/v1/auth/mfa' ? signedIn : [])
    })
    const { wrapper, router } = await mountView(AuthView, { props: { mode: 'login' } }, '/auth/login?redirect=/admin/users')
    await submit(wrapper, { name: 'admin', password: 'secret' })
    expect(wrapper.find('[data-second-step]').exists()).toBe(true)
    expect(document.activeElement).toBe(wrapper.get('[data-second-step] input').element)
    expect(wrapper.find('[role="alert"]').exists()).toBe(false)

    await wrapper.get('form').trigger('submit')
    expect(wrapper.get('[role="alert"]').text()).toBe('请输入验证码')
    await wrapper.get('input').setValue('000000')
    await wrapper.get('form').trigger('submit')
    await flushPromises()
    // A wrong code may be tried again.
    expect(wrapper.get('[role="alert"]').text()).toBe('验证码错误')
    expect(wrapper.find('[data-second-step]').exists()).toBe(true)

    await wrapper.get('input').setValue(' 123456 ')
    await wrapper.get('form').trigger('submit')
    await flushPromises()
    expect(api.request).toHaveBeenCalledWith('/api/v1/auth/mfa', { method: 'POST', anonymous: true, body: { code: '123456' } })
    expect(router.currentRoute.value.path).toBe('/admin/users')
    wrapper.unmount()

    // A sign-in that took too long starts over; so does going back.
    const other = await mountView(AuthView, { props: { mode: 'login' } }, '/auth/login')
    await submit(other.wrapper, { name: 'admin', password: 'secret' })
    await other.wrapper.get('input').setValue('late')
    await other.wrapper.get('form').trigger('submit')
    await flushPromises()
    expect(other.wrapper.find('[data-second-step]').exists()).toBe(false)
    expect(other.wrapper.get('[role="alert"]').text()).toBe('登录超时')
    await submit(other.wrapper, { password: 'secret' })
    const back = other.wrapper.findAll('button').find(item => item.text() === '返回重新登录')
    await back?.trigger('click')
    expect(other.wrapper.find('[data-second-step]').exists()).toBe(false)
    other.wrapper.unmount()
  })

  it('offers the identity providers and comes back from them', async () => {
    navigation.continueAuthorization.mockReset()
    const { wrapper } = await mountView(AuthView, { props: { mode: 'login' } }, '/auth/login?redirect=/admin/users')
    useBootstrap().signInSources = [{ code: 'okta', name: 'Okta' }]
    await flushPromises()
    await wrapper.findAll('button').find(button => button.text() === '通过 Okta 登录')?.trigger('click')
    expect(navigation.continueAuthorization).toHaveBeenCalledWith('/api/v1/auth/federated/okta?redirect=%2Fadmin%2Fusers')
    wrapper.unmount()

    const refused = await mountView(AuthView, { props: { mode: 'login' } }, '/auth/login?federatedError=GF-IDENTITY-117')
    expect(refused.wrapper.get('[role="alert"]').text()).toBe('你在这里还没有账号，请联系管理员开通。')
    refused.wrapper.unmount()
    const unknown = await mountView(AuthView, { props: { mode: 'login' } }, '/auth/login?federatedError=GF-OTHER')
    expect(unknown.wrapper.get('[role="alert"]').text()).toBe('通过身份提供方登录失败，请重试或联系管理员。')
    unknown.wrapper.unmount()

    // An account with two-step sign-in comes back for its second factor.
    const second = await mountView(AuthView, { props: { mode: 'login' } }, '/auth/login?mfa=1')
    useBootstrap().signInSources = [{ code: 'okta', name: 'Okta' }]
    await flushPromises()
    expect(second.wrapper.find('[data-second-step]').exists()).toBe(true)
    expect(second.wrapper.find('[data-providers]').exists()).toBe(false)
    second.wrapper.unmount()
  })

  it('shows server errors', async () => {
    api.request.mockRejectedValue(new Error('用户名或密码错误'))
    const { wrapper } = await mountView(AuthView, { props: { mode: 'login' } }, '/auth/login')
    await submit(wrapper, { name: 'admin', password: 'wrong' })
    expect(wrapper.get('[role="alert"]').text()).toBe('用户名或密码错误')
    wrapper.unmount()
  })

  it('validates registration and confirms a created account', async () => {
    api.request.mockResolvedValue({ username: 'alex' })
    const { wrapper } = await mountView(AuthView, { props: { mode: 'register' } }, '/auth/register')
    await submit(wrapper, { name: 'alex', password: 'long-enough' })
    expect(wrapper.get('[role="alert"]').text()).toBe('请再次输入密码')
    await submit(wrapper, { confirmation: 'different' })
    expect(wrapper.get('[role="alert"]').text()).toBe('两次输入的密码不一致')
    await submit(wrapper, { confirmation: 'long-enough' })
    expect(api.request).toHaveBeenCalledWith('/api/v1/register', { method: 'POST', anonymous: true,
      body: { username: 'alex', password: 'long-enough' } })
    expect(wrapper.get('h2').text()).toBe('账号已创建')
    wrapper.unmount()
  })

  it('links to registration only when the server enables it', async () => {
    const { wrapper } = await mountView(AuthView, { props: { mode: 'login' } }, '/auth/login')
    expect(wrapper.find('a[href="/auth/register"]').exists()).toBe(false)
    useBootstrap().registrationEnabled = true
    await flushPromises()
    expect(wrapper.find('a[href="/auth/register"]').exists()).toBe(true)
    wrapper.unmount()
  })

  it('runs first-run setup with the token from the server log', async () => {
    api.request.mockResolvedValue({ tenantCode: 'default', username: 'admin' })
    const { wrapper } = await mountView(AuthView, { props: { mode: 'setup' } }, '/setup')
    const bootstrap = useBootstrap()
    bootstrap.setupRequired = true
    const inputs = wrapper.findAll('input')
    expect(inputs).toHaveLength(5)
    expect(document.activeElement).toBe(inputs[0]?.element)
    expect(wrapper.find('a[href="/auth/register"]').exists()).toBe(false)

    await wrapper.get('form').trigger('submit')
    await flushPromises()
    expect(wrapper.get('[role="alert"]').text()).toBe('请输入初始化令牌')

    await inputs[0]?.setValue(' the-token ')
    await inputs[2]?.setValue(' admin ')
    await inputs[3]?.setValue('a long password')
    await inputs[4]?.setValue('another password')
    await wrapper.get('form').trigger('submit')
    await flushPromises()
    expect(wrapper.get('[role="alert"]').text()).toBe('两次输入的密码不一致')

    await inputs[4]?.setValue('a long password')
    await wrapper.get('form').trigger('submit')
    await flushPromises()
    expect(api.request).toHaveBeenCalledWith('/api/v1/setup', { method: 'POST', anonymous: true,
      body: { token: 'the-token', tenantName: undefined, username: 'admin', password: 'a long password' } })
    expect(wrapper.get('h2').text()).toBe('初始化完成')
    expect(bootstrap.setupRequired).toBe(false)
    wrapper.unmount()
  })

  it('shows why the server rejected setup', async () => {
    api.request.mockRejectedValue(new Error('初始化令牌无效'))
    const { wrapper } = await mountView(AuthView, { props: { mode: 'setup' } }, '/setup')
    const inputs = wrapper.findAll('input')
    await inputs[0]?.setValue('wrong')
    await inputs[1]?.setValue('Acme')
    await inputs[2]?.setValue('admin')
    await inputs[3]?.setValue('a long password')
    await inputs[4]?.setValue('a long password')
    await wrapper.get('form').trigger('submit')
    await flushPromises()
    expect(api.request).toHaveBeenCalledWith('/api/v1/setup', expect.objectContaining({
      body: expect.objectContaining({ tenantName: 'Acme' }) }))
    expect(wrapper.get('[role="alert"]').text()).toBe('初始化令牌无效')
    wrapper.unmount()
  })

  it('renders in English when the interface language is English', async () => {
    setLocale('en-US')
    try {
      const { wrapper } = await mountView(AuthView, { props: { mode: 'login' } }, '/auth/login')
      expect(wrapper.get('h2').text()).toBe('Welcome back')
      expect(wrapper.get('button[type="submit"]').text()).toContain('Sign in')
      await submit(wrapper, {})
      expect(wrapper.get('[role="alert"]').text()).toBe('Enter a username')
      wrapper.unmount()
    } finally {
      setLocale('zh-CN')
    }
  })

  it('lets the setup page choose the interface language', async () => {
    setLocale('zh-CN')
    try {
      const { wrapper } = await mountView(AuthView, { props: { mode: 'setup' } }, '/setup')
      await wrapper.get('button[aria-label="切换界面语言"]').trigger('click')
      await wrapper.get('button[lang="en-US"]').trigger('click')
      expect(wrapper.get('h2').text()).toBe('Set up GrantForge')
      wrapper.unmount()

      // The sign-in page is the same screen, so it offers the choice as well.
      const login = await mountView(AuthView, { props: { mode: 'login' } }, '/auth/login')
      expect(login.wrapper.find('button[aria-label="Switch interface language"]').exists()).toBe(true)
      login.wrapper.unmount()
    } finally {
      setLocale('zh-CN')
    }
  })

  it('toggles password visibility and resets the form when switching modes', async () => {
    const { wrapper } = await mountView(AuthView, { props: { mode: 'login' } }, '/auth/login')
    await wrapper.get('[aria-label="显示密码"]').trigger('click')
    expect(wrapper.findAll('input')[1]?.attributes('type')).toBe('text')
    await wrapper.findAll('input')[1]?.setValue('x')
    await wrapper.setProps({ mode: 'register' })
    expect((wrapper.findAll('input')[1]?.element as HTMLInputElement).value).toBe('')
    expect(wrapper.findAll('input')).toHaveLength(3)
    wrapper.unmount()
  })
})

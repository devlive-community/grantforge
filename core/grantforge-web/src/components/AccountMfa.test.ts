// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiError } from '@/lib/api'
import { useToast } from '@/stores/toast'

const api = vi.hoisted(() => ({ request: vi.fn() }))
vi.mock('@/lib/api', async importOriginal => ({ ...await importOriginal<typeof import('@/lib/api')>(), ...api }))

const { default: AccountMfa } = await import('./AccountMfa.vue')

const codes = Array.from({ length: 10 }, (_, index) => `abcde-fgh${index}`)

function button(wrapper: VueWrapper, label: string) {
  const found = wrapper.findAll('button').find(item => item.text().trim() === label)
  if (!found) throw new Error('missing button ' + label)
  return found
}

describe('account two-step sign-in', () => {
  let enabled = false
  beforeEach(() => {
    setActivePinia(createPinia())
    enabled = false
    api.request.mockReset()
    api.request.mockImplementation((path: string) => {
      if (path === '/api/v1/me/mfa') return Promise.resolve({ enabled, recoveryCodesLeft: enabled ? 10 : 0 })
      if (path === '/api/v1/me/mfa/enroll') return Promise.resolve({ secret: 'JBSWY3DPEHPK3PXPJBSWY3DPEHPK3PXP', uri: 'otpauth://totp/GrantForge%3Aalice?secret=JBSW' })
      if (path === '/api/v1/me/mfa/confirm') { enabled = true; return Promise.resolve({ codes }) }
      if (path === '/api/v1/me/mfa/recovery-codes') return Promise.resolve({ codes: codes.map(code => code.toUpperCase()) })
      if (path === '/api/v1/me/mfa/disable') { enabled = false; return Promise.resolve(null) }
      return Promise.reject(new Error('unexpected ' + path))
    })
  })

  it('sets up an authenticator and shows the recovery codes once', async () => {
    const wrapper = mount(AccountMfa)
    await flushPromises()
    expect(wrapper.get('[data-mfa-state]').text()).toBe('未开启')

    await button(wrapper, '设置验证器').trigger('click')
    await flushPromises()
    expect(wrapper.get('[data-secret]').text()).toBe('JBSW Y3DP EHPK 3PXP JBSW Y3DP EHPK 3PXP')
    expect(wrapper.get('[data-uri]').text()).toContain('otpauth://totp/')

    await wrapper.get('form').trigger('submit')
    expect(wrapper.get('[role="alert"]').text()).toBe('请输入验证码')
    await wrapper.get('input').setValue(' 123456 ')
    await wrapper.get('form').trigger('submit')
    await flushPromises()

    expect(api.request).toHaveBeenCalledWith('/api/v1/me/mfa/confirm', { method: 'POST', body: { code: '123456' } })
    expect(wrapper.get('[data-mfa-state]').text()).toBe('已开启')
    expect(wrapper.findAll('[data-recovery-codes] li')).toHaveLength(10)
    expect(useToast().items.at(-1)?.message).toBe('两步验证已开启')
    await button(wrapper, '我已保存').trigger('click')
    expect(wrapper.find('[data-recovery-codes]').exists()).toBe(false)
    wrapper.unmount()
  })

  it('renews the recovery codes and turns it off with a code', async () => {
    enabled = true
    const wrapper = mount(AccountMfa)
    await flushPromises()
    expect(wrapper.text()).toContain('还剩 10 个恢复码')

    await wrapper.get('input').setValue('abcde-fgh0')
    await wrapper.get('form').trigger('submit')
    await flushPromises()
    expect(wrapper.findAll('[data-recovery-codes] li').map(item => item.text())).toContain('ABCDE-FGH0')

    await button(wrapper, '关闭两步验证').trigger('click')
    expect(wrapper.get('[role="alert"]').text()).toBe('请输入验证码')
    api.request.mockRejectedValueOnce(new ApiError('验证码错误、已过期或已使用。', 400))
    await wrapper.get('input').setValue('000000')
    await button(wrapper, '关闭两步验证').trigger('click')
    await flushPromises()
    expect(wrapper.get('[role="alert"]').text()).toBe('验证码错误、已过期或已使用。')
    await button(wrapper, '关闭两步验证').trigger('click')
    await flushPromises()
    expect(wrapper.get('[data-mfa-state]').text()).toBe('未开启')
    wrapper.unmount()
  })

  it('reports why the status failed to load', async () => {
    api.request.mockRejectedValue(new ApiError('服务暂时不可用。', 503))
    const wrapper = mount(AccountMfa)
    await flushPromises()
    expect(wrapper.get('[role="alert"]').text()).toBe('服务暂时不可用。')
    wrapper.unmount()
  })
})

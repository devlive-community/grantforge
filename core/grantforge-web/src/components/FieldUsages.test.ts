// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { i18n } from '@/i18n'

const api = vi.hoisted(() => ({ request: vi.fn() }))
vi.mock('@/lib/api', async importOriginal => ({ ...await importOriginal<typeof import('@/lib/api')>(), ...api }))

const { default: FieldUsages } = await import('./FieldUsages.vue')

const usages = [{ httpMethod: 'GET', pathPattern: '/api/v1/users', direction: 'READ' },
  { httpMethod: 'PUT', pathPattern: '/api/v1/users/{id}', direction: 'WRITE' }]

describe('field usages', () => {
  beforeEach(() => { api.request.mockReset() })

  it('lists the APIs that return or accept the field', async () => {
    api.request.mockResolvedValue(usages)
    const wrapper = mount(FieldUsages, { props: { resourceId: '42' }, global: { plugins: [i18n] } })
    expect(wrapper.find('.animate-pulse').exists()).toBe(true)
    await flushPromises()

    expect(api.request).toHaveBeenCalledWith('/api/v1/resources/42/field-usages')
    expect(wrapper.find('[data-usage="GET /api/v1/users"]').text()).toContain('返回')
    expect(wrapper.find('[data-usage="PUT /api/v1/users/{id}"]').text()).toContain('接收')

    api.request.mockResolvedValue([])
    await wrapper.setProps({ resourceId: '43' })
    await flushPromises()
    expect(api.request).toHaveBeenLastCalledWith('/api/v1/resources/43/field-usages')
    expect(wrapper.text()).toContain('目前没有接口返回或接收这个字段')
  })

  it('says why the APIs could not be read', async () => {
    api.request.mockRejectedValue(new Error('网络不可用'))
    const wrapper = mount(FieldUsages, { props: { resourceId: '42' }, global: { plugins: [i18n] } })
    await flushPromises()
    expect(wrapper.find('[role="alert"]').exists()).toBe(true)
  })
})

// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import { i18n } from '@/i18n'
import ImpactSummary from './ImpactSummary.vue'

const role = (code: string, gained: number, lost: number, tenantCode: string | undefined = 'acme') =>
  ({ roleId: code, tenantCode, code, name: code.toUpperCase(), gained, lost })

describe('impact summary', () => {
  it('says when nothing changes', () => {
    const wrapper = mount(ImpactSummary, { props: { impact: { roles: [], accounts: 0, gained: [], lost: [] } }, global: { plugins: [i18n] } })
    expect(wrapper.text()).toBe('这次修改不会改变任何角色的权限。')
  })

  it('lists the roles with their tenants, what they gain and lose, and shortens long lists', () => {
    const lost = Array.from({ length: 14 }, (_, index) => `code.${index}`)
    const wrapper = mount(ImpactSummary, {
      props: { impact: { roles: [role('auditors', 0, 14), role('editors', 2, 0, undefined)], accounts: 5, gained: ['a', 'b'], lost }, tenants: true },
      global: { plugins: [i18n] },
    })
    expect(wrapper.text()).toContain('将改变 2 个角色的权限，持有这些角色的用户 5 个')
    expect(wrapper.text()).toContain('等另外 2 项')
    expect(wrapper.get('[data-impact-role="auditors"]').text()).toContain('acme')
    expect(wrapper.get('[data-impact-role="auditors"]').text()).toContain('−14')
    expect(wrapper.get('[data-impact-role="editors"]').text()).toContain('+2')
    expect(wrapper.get('[data-impact]').classes()).toContain('bg-amber-50/60')

    const local = mount(ImpactSummary, { props: { impact: { roles: [role('auditors', 1, 0)], accounts: 1, gained: ['a'], lost: [] } },
      global: { plugins: [i18n] } })
    expect(local.get('[data-impact-role="auditors"]').text()).not.toContain('acme')
  })
})

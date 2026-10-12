// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import { encode } from 'uqr'
import QrCode from './QrCode.vue'

const link = 'otpauth://totp/GrantForge:alice?secret=JBSWY3DPEHPK3PXP&issuer=GrantForge'

describe('QR code', () => {
  it('draws the code of its text dark on white, with a quiet zone of four modules', () => {
    const wrapper = mount(QrCode, { props: { value: link, label: '两步验证二维码' } })
    const svg = wrapper.get('svg')
    const size = encode(link, { ecc: 'M', border: 0 }).size
    expect(svg.attributes('viewBox')).toBe(`0 0 ${size + 8} ${size + 8}`)
    expect(svg.attributes('role')).toBe('img')
    expect(svg.attributes('aria-label')).toBe('两步验证二维码')
    expect(svg.classes()).toContain('bg-white')
    const path = wrapper.get('path').attributes('d') ?? ''
    // Every dark module is one square, and the top-left finder pattern starts right inside the quiet zone.
    const dark = encode(link, { ecc: 'M', border: 0 }).data.flat().filter(Boolean).length
    expect(path.match(/M/g)).toHaveLength(dark)
    expect(path.startsWith('M4 4h1v1h-1z')).toBe(true)
  })

  it('draws another code for another text', async () => {
    const wrapper = mount(QrCode, { props: { value: link, label: 'QR' } })
    const before = wrapper.get('path').attributes('d')
    await wrapper.setProps({ value: `${link}&period=60` })
    expect(wrapper.get('path').attributes('d')).not.toBe(before)
  })
})

// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it } from 'vitest'
import { useToast } from '@/stores/toast'
import ToastHub from './ToastHub.vue'

describe('toast hub', () => {
  beforeEach(() => { setActivePinia(createPinia()) })

  it('announces messages politely and dismisses them from the close button', async () => {
    const toast = useToast()
    toast.show('保存成功')
    toast.show('保存失败', 'error')
    const wrapper = mount(ToastHub)
    expect(wrapper.get('[aria-live]').attributes('aria-live')).toBe('polite')
    expect(wrapper.text()).toContain('保存成功')
    expect(wrapper.text()).toContain('保存失败')

    await wrapper.findAll('[aria-label="关闭提示"]')[0]?.trigger('click')

    expect(wrapper.text()).not.toContain('保存成功')
    expect(toast.items).toHaveLength(1)
  })
})

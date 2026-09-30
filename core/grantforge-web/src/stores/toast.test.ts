// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

import { createPinia, setActivePinia } from 'pinia'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { useToast } from './toast'

describe('toast store', () => {
  beforeEach(() => { setActivePinia(createPinia()); vi.useFakeTimers() })
  afterEach(() => { vi.useRealTimers() })

  it('shows success messages by default and removes them after five seconds', () => {
    const toast = useToast()
    toast.show('已保存')
    expect(toast.items).toEqual([{ id: 1, message: '已保存', kind: 'success' }])
    vi.advanceTimersByTime(4999)
    expect(toast.items).toHaveLength(1)
    vi.advanceTimersByTime(1)
    expect(toast.items).toEqual([])
  })

  it('keeps independent messages with unique ids and removes one on request', () => {
    const toast = useToast()
    toast.show('a', 'error')
    toast.show('b')
    toast.remove(1)
    expect(toast.items).toEqual([{ id: 2, message: 'b', kind: 'success' }])
    toast.remove(99)
    expect(toast.items).toHaveLength(1)
  })
})

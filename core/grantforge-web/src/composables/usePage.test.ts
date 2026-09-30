import { defineComponent, nextTick } from 'vue'
import { mount, flushPromises } from '@vue/test-utils'
import { describe, expect, it, vi } from 'vitest'
import { usePage } from './usePage'
import { request } from '@/lib/api'
import type { Page } from '@/types/api'
vi.mock('@/lib/api', () => ({ request: vi.fn(), errorMessage: (value: unknown) => value instanceof Error ? value.message : 'error' }))
const fixture: Page<{ id: number }> = { content: [{ id: 2 }], number: 2, size: 20, totalPages: 3, totalElements: 60 }
describe('reactive pagination', () => {
  it('cancels previous requests and prevents stale results from overwriting the current page', async () => {
    let resolveOld: ((value: Page<{ id: number }>) => void) | undefined
    const deferred = new Promise<Page<{ id: number }>>(resolve => { resolveOld = resolve })
    vi.mocked(request).mockReturnValueOnce(deferred as Promise<never>).mockResolvedValueOnce(fixture as never)
    let state: ReturnType<typeof usePage<{ id: number }>> | undefined
    const wrapper = mount(defineComponent({ setup() { state = usePage<{ id: number }>('/api/v1/user'); return () => null } }))
    const oldSignal = vi.mocked(request).mock.calls[0]?.[1]?.signal
    state!.page.value = 2; await nextTick(); await flushPromises()
    expect(oldSignal?.aborted).toBe(true)
    resolveOld?.({ ...fixture, content: [{ id: 1 }], number: 1 }); await flushPromises()
    expect(state!.rows.value).toEqual([{ id: 2 }])
    expect(state!.loading.value).toBe(false)
    wrapper.unmount()
  })
})

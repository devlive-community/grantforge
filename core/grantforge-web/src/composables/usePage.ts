// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

import { computed, onWatcherCleanup, ref, shallowRef, toValue, watch, type MaybeRefOrGetter } from 'vue'
import { errorMessage, request } from '@/lib/api'
import type { Page } from '@/types/api'

export function usePage<T>(path: MaybeRefOrGetter<string>) {
  const page = ref(1), size = ref(20), revision = ref(0)
  const data = shallowRef<Page<T>>({ content: [], number: 1, size: 20, totalElements: 0, totalPages: 0 })
  const loading = ref(false), error = ref('')
  watch([() => toValue(path), page, size, revision], ([url, current, limit]) => {
    const controller = new AbortController()
    onWatcherCleanup(() => controller.abort())
    loading.value = true; error.value = ''
    void request<Page<T>>(url, { query: { page: current, size: limit }, signal: controller.signal }).then(result => {
      if (controller.signal.aborted) return
      if (result.totalPages > 0 && current > result.totalPages) { page.value = result.totalPages; return }
      data.value = result
    }).catch(reason => { if (!controller.signal.aborted) error.value = errorMessage(reason) })
      .finally(() => { if (!controller.signal.aborted) loading.value = false })
  }, { immediate: true })
  return { page, size, data, loading, error, rows: computed(() => data.value.content),
    refresh: () => revision.value++, setSize: (value: number) => { page.value = 1; size.value = value } }
}

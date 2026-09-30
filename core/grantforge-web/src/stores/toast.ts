// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

import { ref } from 'vue'
import { defineStore } from 'pinia'

export const useToast = defineStore('toast', () => {
  const items = ref<{ id: number; message: string; kind: 'success' | 'error' }[]>([])
  let serial = 0
  function remove(id: number) { items.value = items.value.filter(item => item.id !== id) }
  function show(message: string, kind: 'success' | 'error' = 'success') {
    const id = ++serial; items.value.push({ id, message, kind })
    setTimeout(() => remove(id), 5000)
  }
  return { items, show, remove }
})

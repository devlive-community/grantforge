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

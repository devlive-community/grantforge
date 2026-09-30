<script setup lang="ts">
import { onMounted, onBeforeUnmount, useId, useTemplateRef, watch } from 'vue'
import { X } from '@lucide/vue'
const open = defineModel<boolean>({ required: true })
const { title, description = '', wide = false, busy = false } = defineProps<{ title: string; description?: string; wide?: boolean; busy?: boolean }>()
const dialog = useTemplateRef<HTMLDialogElement>('dialog')
const id = useId()
function sync() {
  if (open.value && !dialog.value?.open) dialog.value?.showModal()
  else if (!open.value && dialog.value?.open) dialog.value.close()
}
watch(open, sync, { flush: 'post' })
onMounted(sync)
onBeforeUnmount(() => dialog.value?.close())
function backdrop(event: MouseEvent) { if (event.target === dialog.value && !busy) open.value = false }
function cancel(event: Event) { if (busy) event.preventDefault(); else open.value = false }
</script>
<template>
  <Teleport to="body">
    <dialog
      ref="dialog"
      :aria-labelledby="id"
      :class="wide ? 'max-w-2xl' : ''"
      @click="backdrop"
      @cancel="cancel"
      @close="open = false"
    >
      <header class="flex items-start justify-between border-b border-line px-6 py-5">
        <div><h2 :id="id" class="text-lg font-semibold tracking-tight">{{ title }}</h2><p v-if="description" class="mt-1 text-xs leading-relaxed text-muted">{{ description }}</p></div>
        <button
          type="button"
          class="icon-button -mr-2 -mt-1"
          aria-label="关闭对话框"
          :disabled="busy"
          @click="open = false"
        >
          <X :size="18" />
        </button>
      </header>
      <div class="max-h-[65dvh] overflow-y-auto p-6"><slot></slot></div>
      <footer v-if="$slots.footer" class="flex justify-end gap-2 border-t border-line bg-canvas/40 px-6 py-4"><slot name="footer"></slot></footer>
    </dialog>
  </Teleport>
</template>

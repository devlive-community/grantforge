<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

<script setup lang="ts">
import { onMounted, onBeforeUnmount, ref, useId, useTemplateRef, watch } from 'vue'
import { X } from '@lucide/vue'
import { useI18n } from 'vue-i18n'
const open = defineModel<boolean>({ required: true })
const { title, description = '', wide = false, busy = false } = defineProps<{ title: string; description?: string; wide?: boolean; busy?: boolean }>()
/**
 * A modal dialog. It opens and closes with a short animation, its backdrop fading with it: closing plays the leaving
 * animation first and only then closes the dialog, and opening again meanwhile simply keeps it open. A click on the
 * backdrop does nothing, so a half-filled form is never lost to a stray click; the close button and Escape close it,
 * except while it is busy.
 */
const dialog = useTemplateRef<HTMLDialogElement>('dialog')
const id = useId()
const { t } = useI18n()
const closing = ref(false)
// While it leaves, the dialog keeps the heading it had, though the page may already have moved on from what it showed.
const shown = ref({ title, description })
watch(() => [title, description] as const, ([nextTitle, nextDescription]) => {
  if (open.value && !closing.value) shown.value = { title: nextTitle, description: nextDescription }
})
let finish: (() => void) | undefined

function leave(element: HTMLDialogElement) {
  closing.value = true
  // Set at once rather than on the next render, so the leaving animation can be read below.
  element.toggleAttribute('data-closing', true)
  let timer: ReturnType<typeof setTimeout> | undefined
  const done = () => {
    finish = undefined
    element.removeEventListener('animationend', ended)
    clearTimeout(timer)
    element.removeAttribute('data-closing')
    closing.value = false
    shown.value = { title, description }
    if (!open.value && element.open) element.close()
  }
  const ended = (event: AnimationEvent) => { if (event.target === element) done() }
  // Without an animation to wait for (reduced motion, or no styles at all), the dialog closes at once.
  const style = getComputedStyle(element)
  if (!style.animationName || style.animationName === 'none' || Number.parseFloat(style.animationDuration || '0') === 0) {
    done()
    return
  }
  element.addEventListener('animationend', ended)
  // In case the animation never reports its end, such as in a tab that is hidden meanwhile.
  timer = setTimeout(done, 400)
  finish = done
}
function sync() {
  const element = dialog.value
  if (!element) return
  if (open.value) {
    // Opened again while it was leaving: it stays open, and the leaving animation stops.
    if (closing.value) { finish?.(); return }
    shown.value = { title, description }
    if (!element.open) element.showModal()
  } else if (element.open && !closing.value) {
    leave(element)
  }
}
watch(open, sync, { flush: 'post' })
onMounted(sync)
onBeforeUnmount(() => { finish?.(); dialog.value?.close() })
function cancel(event: Event) {
  // Escape closes through the leaving animation rather than at once, so the native close is always held back.
  event.preventDefault()
  if (!busy) open.value = false
}
</script>
<template>
  <Teleport to="body">
    <dialog
      ref="dialog"
      :aria-labelledby="id"
      :class="wide ? 'max-w-3xl' : ''"
      :data-closing="closing ? '' : undefined"
      :inert="closing || undefined"
      @cancel="cancel"
      @close="open = false"
    >
      <header class="flex items-start justify-between border-b border-line px-6 py-5">
        <div><h2 :id="id" class="text-lg font-semibold tracking-tight">{{ shown.title }}</h2><p v-if="shown.description" class="mt-1 text-xs leading-relaxed text-muted">{{ shown.description }}</p></div>
        <button
          type="button"
          class="icon-button -mr-2 -mt-1"
          :aria-label="t('controls.closeDialog')"
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

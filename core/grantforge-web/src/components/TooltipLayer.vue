<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, shallowRef, useTemplateRef, watch, type CSSProperties } from 'vue'
import { arrow, autoUpdate, computePosition, flip, hide, offset, shift } from '@floating-ui/dom'

/**
 * The one tooltip of the console, mounted once beside the routed view. It names what an icon button does: any
 * `.table-action` or `.icon-button` with an `aria-label`, and anything carrying `data-tooltip`, which also gives a
 * shorter text than the label where the label names the row as well. Pages therefore only describe their buttons;
 * they never place a tooltip themselves.
 *
 * It shows after a short pause under a mouse or pen, at once on keyboard focus, and goes on a press, on Escape, or
 * when the pointer or focus leaves. Floating UI keeps it inside the window, and inside the dialog its button sits in:
 * above the button, below it where there is no room above, slid sideways at an edge, and hidden once the button is
 * scrolled out of sight. It renders inside that dialog, so the dialog's backdrop never covers it. Screen readers
 * already hear the button's label, so the tooltip itself is hidden from them.
 */
const SELECTOR = '[data-tooltip], .table-action[aria-label], .icon-button[aria-label]'
// A pause before the first tooltip, none while moving from one button to the next.
const DELAY = 350, WARM = 400

const target = shallowRef<HTMLElement | null>(null), text = ref('')
const bubble = useTemplateRef<HTMLElement>('bubble'), tail = useTemplateRef<HTMLElement>('tail')
const host = computed(() => target.value?.closest('dialog') ?? document.body)
const strategy = computed(() => host.value instanceof HTMLDialogElement ? 'absolute' : 'fixed')
const side = ref<'top' | 'bottom'>('top')
// Set once the tooltip has a place, so it animates in where it belongs rather than from the corner it waited in.
const placed = ref(false)
const style = ref<CSSProperties>({ position: 'fixed', left: '0px', top: '0px', visibility: 'hidden' })
const tailStyle = ref<CSSProperties>({})
let timer: ReturnType<typeof setTimeout> | undefined, warmUntil = 0, stop: (() => void) | undefined

function textOf(element: HTMLElement) {
  return (element.dataset.tooltip || element.getAttribute('aria-label') || '').trim()
}
function show(element: HTMLElement, now = false) {
  clearTimeout(timer)
  const label = textOf(element)
  if (!label || element.dataset.tooltip === 'off') return
  const open = () => { target.value = element; text.value = label }
  if (now || Date.now() < warmUntil) open(); else timer = setTimeout(open, DELAY)
}
function close() {
  clearTimeout(timer)
  if (target.value) warmUntil = Date.now() + WARM
  target.value = null
  placed.value = false
}

async function place() {
  const reference = target.value, floating = bubble.value
  if (!reference || !floating) return
  if (!reference.isConnected) { close(); return }
  // A tooltip may use the whole dialog it is in, but never hang out of it over the page.
  const boundary = host.value instanceof HTMLDialogElement ? host.value : 'clippingAncestors'
  const at = await computePosition(reference, floating, {
    placement: 'top',
    strategy: strategy.value,
    middleware: [
      offset(8),
      flip({ fallbackPlacements: ['bottom'], boundary, padding: 6 }),
      shift({ boundary, padding: 6 }),
      ...(tail.value ? [arrow({ element: tail.value, padding: 6 })] : []),
      hide(),
    ],
  })
  side.value = at.placement.startsWith('bottom') ? 'bottom' : 'top'
  style.value = {
    position: at.strategy,
    left: `${at.x}px`,
    top: `${at.y}px`,
    visibility: at.middlewareData.hide?.referenceHidden ? 'hidden' : 'visible',
  }
  placed.value = !at.middlewareData.hide?.referenceHidden
  const x = at.middlewareData.arrow?.x
  tailStyle.value = x === undefined ? {} : { left: `${x}px` }
}
watch([target, bubble], ([reference, floating]) => {
  stop?.()
  stop = reference && floating ? autoUpdate(reference, floating, () => void place()) : undefined
}, { flush: 'post' })

function matched(event: Event) {
  return event.target instanceof Element ? event.target.closest<HTMLElement>(SELECTOR) : null
}
function pointerOver(event: PointerEvent) {
  // A touch has no hover: a tap would only flash the tooltip before the action runs.
  if (event.pointerType === 'touch') return
  const element = matched(event)
  if (element && element !== target.value) show(element)
  else if (!element && target.value) close()
}
function pointerOut(event: PointerEvent) {
  const element = matched(event)
  if (!element) return
  if (event.relatedTarget instanceof Node && element.contains(event.relatedTarget)) return
  if (element === target.value) close(); else clearTimeout(timer)
}
function focusIn(event: FocusEvent) {
  const element = matched(event)
  // Only focus from the keyboard: a click focuses the button too, and its tooltip has just gone with the press.
  if (element && element.matches(':focus-visible')) show(element, true)
}
function focusOut(event: FocusEvent) { if (matched(event) === target.value) close() }
function keyDown(event: KeyboardEvent) { if (event.key === 'Escape') close() }

const listeners = [['pointerover', pointerOver], ['pointerout', pointerOut], ['pointerdown', close], ['focusin', focusIn],
  ['focusout', focusOut], ['keydown', keyDown]] as const
onMounted(() => { for (const [name, listener] of listeners) document.addEventListener(name, listener as (event: Event) => void, true) })
onBeforeUnmount(() => {
  for (const [name, listener] of listeners) document.removeEventListener(name, listener as (event: Event) => void, true)
  clearTimeout(timer)
  stop?.()
})
</script>

<template>
  <Teleport :to="host">
    <Transition leave-active-class="transition duration-100 ease-in" leave-to-class="scale-95 opacity-0">
      <div
        v-if="target"
        ref="bubble"
        aria-hidden="true"
        data-tooltip-layer
        :style="style"
        :class="placed ? (side === 'top' ? 'tooltip-in-top origin-bottom' : 'tooltip-in-bottom origin-top') : ''"
        class="pointer-events-none z-50 w-max max-w-60 rounded-md bg-[#101828] px-2 py-1 text-[11px] leading-4 font-medium text-white shadow-[0_6px_16px_-6px_rgb(15_23_42/0.4)] dark:bg-[#36405a]"
      >
        <span
          ref="tail"
          :style="tailStyle"
          class="absolute size-2 rotate-45 bg-inherit"
          :class="side === 'top' ? '-bottom-1' : '-top-1'"
        ></span>
        <span class="relative">{{ text }}</span>
      </div>
    </Transition>
  </Teleport>
</template>

<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, ref, shallowRef, useId, useTemplateRef, type CSSProperties } from 'vue'
import { CalendarDays, ChevronDown, ChevronLeft, ChevronRight, ChevronsLeft, ChevronsRight, ChevronUp, X } from '@lucide/vue'
import { useI18n } from 'vue-i18n'
import { addDays, addMonths, compareDays, dayLabel, displayValue, formatValue, monthGrid, monthTitle, now, parseValue,
  weekday, weekdayNames, weekStart, withinBounds, type Day, type Moment } from '@/lib/calendar'
import { offscreen, placedStyle, placement } from '@/lib/floating'

/**
 * Picks a day, or a day and a time with `time`, from a calendar that opens below the field, or above it near the
 * window's bottom, and shifts to stay inside every edge of the window, also on phones and inside dialogs. The value
 * is written as the native inputs wrote it, `YYYY-MM-DD` or `YYYY-MM-DDTHH:mm`, empty for none. Days outside `min`
 * and `max` cannot be picked. The calendar works with the keyboard: arrows move by day and week, Page Up and Page
 * Down by month (with Shift by year), Home and End to the week's ends, Enter picks and Escape closes.
 */
const value = defineModel<string>({ required: true })
const { label, time = false, placeholder = '', error = '', hint = '', required = false, disabled = false, min = '', max = '' } = defineProps<{
  label: string; time?: boolean; placeholder?: string; error?: string; hint?: string; required?: boolean; disabled?: boolean
  min?: string; max?: string
}>()
const { t, locale } = useI18n()
const id = useId()
const trigger = useTemplateRef<HTMLButtonElement>('trigger'), panel = useTemplateRef<HTMLElement>('panel')
const open = ref(false), portal = shallowRef<HTMLElement>(), style = ref<CSSProperties>({})
const focused = ref<Day>(now()), view = ref({ year: 0, month: 0 })

const picked = computed(() => parseValue(value.value))
const firstDay = computed(() => weekStart(locale.value))
const cells = computed(() => monthGrid(view.value.year, view.value.month, firstDay.value))
const weeks = computed(() => Array.from({ length: 6 }, (_, row) => cells.value.slice(row * 7, row * 7 + 7)))
const names = computed(() => weekdayNames(locale.value, firstDay.value))
const title = computed(() => monthTitle(locale.value, view.value.year, view.value.month))
const shown = computed(() => picked.value ? displayValue(locale.value, picked.value, time) : value.value)
const today = computed(() => now())

const key = (day: Day) => formatValue({ ...day, hour: 0, minute: 0 }, false)
const same = (a: Day | undefined, b: Day) => a !== undefined && compareDays(a, b) === 0

function place() {
  const opener = trigger.value, floating = panel.value, container = portal.value
  if (!open.value || !opener || !floating || !container) return
  const box = opener.getBoundingClientRect(), screen = { width: window.innerWidth, height: window.innerHeight }
  if (offscreen(box, screen)) { close(); return }
  // Measured at its natural size, whatever an earlier placement capped it to.
  const at = placement(box, { width: floating.scrollWidth, height: floating.scrollHeight }, screen)
  style.value = placedStyle(at, container)
}
function onOutside(event: PointerEvent) {
  const target = event.target as Node
  if (!panel.value?.contains(target) && !trigger.value?.contains(target)) close()
}
function listen(on: boolean) {
  if (on) {
    window.addEventListener('resize', place)
    window.addEventListener('scroll', place, true)
    document.addEventListener('pointerdown', onOutside, true)
  } else {
    window.removeEventListener('resize', place)
    window.removeEventListener('scroll', place, true)
    document.removeEventListener('pointerdown', onOutside, true)
  }
}
function show() {
  if (disabled || open.value) return
  portal.value = trigger.value?.closest('dialog') || document.body
  // With no value, open where days can be picked: today, or the first allowed day when that lies ahead.
  const low = min ? parseValue(min) : undefined
  const start = picked.value ?? (low && compareDays(low, today.value) > 0 ? low : today.value)
  focused.value = { year: start.year, month: start.month, day: start.day }
  view.value = { year: start.year, month: start.month }
  // Rendered out of sight first, so its size can be measured before it is placed.
  style.value = { position: 'fixed', left: '0px', top: '0px', visibility: 'hidden' }
  open.value = true
  listen(true)
  void nextTick(() => { place(); focusDay() })
}
function close(returnFocus = false) {
  if (!open.value) return
  open.value = false
  listen(false)
  if (returnFocus) trigger.value?.focus()
}
function focusDay() {
  void nextTick(() => panel.value?.querySelector<HTMLElement>(`[data-day="${key(focused.value)}"]`)?.focus())
}
function moveTo(day: Day) {
  focused.value = day
  if (day.year !== view.value.year || day.month !== view.value.month) {
    view.value = { year: day.year, month: day.month }
    void nextTick(place)
  }
  focusDay()
}
function turn(months: number) {
  const target = addMonths({ year: view.value.year, month: view.value.month, day: focused.value.day }, months)
  focused.value = target
  view.value = { year: target.year, month: target.month }
  void nextTick(place)
}
function write(moment: Moment) { value.value = formatValue(moment, time) }
function pick(day: Day) {
  if (!withinBounds(day, min, max)) return
  const current = picked.value
  write({ ...day, hour: current?.hour ?? 0, minute: current?.minute ?? 0 })
  focused.value = day
  if (!time) close(true)
}
function pickNow() {
  const moment = now()
  if (!withinBounds(moment, min, max)) return
  write(time ? moment : { ...moment, hour: 0, minute: 0 })
  focused.value = moment; view.value = { year: moment.year, month: moment.month }
  if (!time) close(true)
}
function clear() { value.value = ''; close(true) }

function gridKey(event: KeyboardEvent) {
  const day = focused.value
  const moves: Record<string, () => Day> = {
    ArrowLeft: () => addDays(day, -1), ArrowRight: () => addDays(day, 1), ArrowUp: () => addDays(day, -7), ArrowDown: () => addDays(day, 7),
    Home: () => addDays(day, -((weekday(day) - firstDay.value + 7) % 7)), End: () => addDays(day, 6 - (weekday(day) - firstDay.value + 7) % 7),
    PageUp: () => addMonths(day, event.shiftKey ? -12 : -1), PageDown: () => addMonths(day, event.shiftKey ? 12 : 1),
  }
  const move = moves[event.key]
  if (move) { event.preventDefault(); moveTo(move()) }
}
function panelKey(event: KeyboardEvent) {
  if (event.key === 'Escape') { event.preventDefault(); event.stopPropagation(); close(true) }
}
function triggerKey(event: KeyboardEvent) {
  if ((event.key === 'ArrowDown' || event.key === 'ArrowUp') && !open.value) { event.preventDefault(); show() }
}
function leave(event: FocusEvent) {
  const next = event.relatedTarget as Node | null
  if (next && !panel.value?.contains(next) && !trigger.value?.contains(next)) close()
}

/** The hour or minute of the value, changed by a step that wraps around, or set to what was typed. */
function partOf(part: 'hour' | 'minute') { return picked.value?.[part] ?? 0 }
function setPart(part: 'hour' | 'minute', next: number) {
  const limit = part === 'hour' ? 24 : 60
  const base = picked.value ?? { ...today.value, hour: 0, minute: 0 }
  write({ ...base, [part]: ((next % limit) + limit) % limit })
}
function typedPart(part: 'hour' | 'minute', event: Event) {
  const input = event.target as HTMLInputElement
  const digits = input.value.replace(/\D/g, '').slice(-2)
  const number = Number(digits)
  if (digits && number < (part === 'hour' ? 24 : 60)) setPart(part, number)
  input.value = String(partOf(part)).padStart(2, '0')
}
function partKey(part: 'hour' | 'minute', event: KeyboardEvent) {
  const step = { ArrowUp: 1, ArrowDown: -1, PageUp: part === 'hour' ? 6 : 10, PageDown: part === 'hour' ? -6 : -10 }[event.key]
  if (step) { event.preventDefault(); setPart(part, partOf(part) + step) }
}

onBeforeUnmount(() => listen(false))
</script>

<template>
  <div class="relative">
    <label :id="`${id}-label`" :for="id" class="field-label">{{ label }} <span v-if="required" class="text-rose-500" aria-hidden="true">*</span></label>
    <div class="relative">
      <button
        :id="id"
        ref="trigger"
        type="button"
        :disabled="disabled"
        aria-haspopup="dialog"
        :aria-expanded="open"
        :aria-controls="open ? `${id}-panel` : undefined"
        :aria-invalid="Boolean(error)"
        :aria-describedby="error ? `${id}-error` : hint ? `${id}-hint` : undefined"
        class="flex w-full items-center gap-3 rounded-xl border bg-surface px-3.5 py-2.5 text-left text-sm text-ink transition hover:border-brand/40 disabled:cursor-not-allowed disabled:opacity-50"
        :class="[open ? 'border-brand' : error ? 'border-rose-400' : 'border-line', value && !required ? 'pr-16' : '']"
        @click="open ? close() : show()"
        @keydown="triggerKey"
        @blur="leave"
      >
        <CalendarDays :size="16" aria-hidden="true" class="shrink-0" :class="open ? 'text-brand' : 'text-muted'" />
        <span class="min-w-0 flex-1 truncate tabular-nums" :class="value ? '' : 'text-muted'">
          {{ shown || placeholder || (time ? t('controls.datetimePlaceholder') : t('controls.datePlaceholder')) }}
        </span>
      </button>
      <button
        v-if="value && !required && !disabled"
        type="button"
        class="absolute inset-y-0 right-2 my-auto flex size-7 items-center justify-center rounded-lg text-muted transition hover:bg-canvas hover:text-ink"
        :aria-label="t('controls.clearDate', { label })"
        @click="clear"
      >
        <X :size="14" aria-hidden="true" />
      </button>
    </div>
    <p v-if="error" :id="`${id}-error`" class="mt-2 text-xs text-rose-600">{{ error }}</p>
    <p v-else-if="hint" :id="`${id}-hint`" class="mt-1.5 text-[11px] text-muted">{{ hint }}</p>

    <Teleport v-if="open && portal" :to="portal">
      <div
        :id="`${id}-panel`"
        ref="panel"
        role="dialog"
        :aria-labelledby="`${id}-label`"
        :style="style"
        class="z-[100] w-[18.5rem] overflow-y-auto overscroll-contain rounded-xl border border-line bg-surface p-3 text-sm text-ink"
        @keydown="panelKey"
        @focusout="leave"
      >
        <div class="mb-2 flex items-center gap-1">
          <button type="button" class="icon-button size-8" :aria-label="t('controls.previousYear')" @click="turn(-12)"><ChevronsLeft :size="15" aria-hidden="true" /></button>
          <button type="button" class="icon-button size-8" :aria-label="t('controls.previousMonth')" @click="turn(-1)"><ChevronLeft :size="15" aria-hidden="true" /></button>
          <p class="flex-1 text-center text-[13px] font-semibold" aria-live="polite">{{ title }}</p>
          <button type="button" class="icon-button size-8" :aria-label="t('controls.nextMonth')" @click="turn(1)"><ChevronRight :size="15" aria-hidden="true" /></button>
          <button type="button" class="icon-button size-8" :aria-label="t('controls.nextYear')" @click="turn(12)"><ChevronsRight :size="15" aria-hidden="true" /></button>
        </div>
        <table role="grid" class="w-full table-fixed border-collapse" :aria-label="title" @keydown="gridKey">
          <thead>
            <tr>
              <th v-for="name in names" :key="name.long" scope="col" class="pb-1 text-[11px] font-medium text-muted"><abbr :title="name.long" class="no-underline">{{ name.short }}</abbr></th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="(week, row) in weeks" :key="row">
              <td v-for="cell in week" :key="key(cell)" class="p-0.5 text-center">
                <button
                  type="button"
                  :data-day="key(cell)"
                  :tabindex="same(focused, cell) ? 0 : -1"
                  :aria-label="dayLabel(locale, cell)"
                  :aria-pressed="same(picked, cell)"
                  :aria-current="same(today, cell) ? 'date' : undefined"
                  :aria-disabled="!withinBounds(cell, min, max) || undefined"
                  class="mx-auto flex size-9 items-center justify-center rounded-lg text-[13px] tabular-nums transition"
                  :class="[
                    !withinBounds(cell, min, max) ? 'cursor-not-allowed text-muted/40 line-through'
                    : same(picked, cell) ? 'bg-brand font-semibold text-white'
                      : cell.inMonth ? 'text-ink hover:bg-brand-soft hover:text-brand' : 'text-muted/60 hover:bg-canvas',
                    same(today, cell) && !same(picked, cell) ? 'font-semibold text-brand' : '',
                  ]"
                  @click="pick(cell)"
                >
                  {{ cell.day }}
                </button>
              </td>
            </tr>
          </tbody>
        </table>

        <div v-if="time" class="mt-3 flex items-center justify-center gap-2 border-t border-line pt-3" role="group" :aria-label="t('controls.timeOfDay')">
          <template v-for="(part, index) in (['hour', 'minute'] as const)" :key="part">
            <span v-if="index > 0" class="text-base font-semibold text-muted" aria-hidden="true">:</span>
            <div class="flex items-center rounded-lg border border-line">
              <input
                :value="String(partOf(part)).padStart(2, '0')"
                inputmode="numeric"
                role="spinbutton"
                :aria-label="t(part === 'hour' ? 'controls.hour' : 'controls.minute')"
                :aria-valuenow="partOf(part)"
                aria-valuemin="0"
                :aria-valuemax="part === 'hour' ? 23 : 59"
                class="w-10 rounded-l-lg bg-transparent py-1.5 text-center text-sm tabular-nums outline-none focus-visible:bg-brand-soft"
                @change="typedPart(part, $event)"
                @keydown="partKey(part, $event)"
              />
              <div class="flex flex-col border-l border-line">
                <button
                  type="button"
                  class="px-1 text-muted hover:text-brand"
                  tabindex="-1"
                  :aria-label="t('controls.increase', { label: t(part === 'hour' ? 'controls.hour' : 'controls.minute') })"
                  @click="setPart(part, partOf(part) + 1)"
                >
                  <ChevronUp :size="12" aria-hidden="true" />
                </button>
                <button
                  type="button"
                  class="px-1 text-muted hover:text-brand"
                  tabindex="-1"
                  :aria-label="t('controls.decrease', { label: t(part === 'hour' ? 'controls.hour' : 'controls.minute') })"
                  @click="setPart(part, partOf(part) - 1)"
                >
                  <ChevronDown :size="12" aria-hidden="true" />
                </button>
              </div>
            </div>
          </template>
        </div>

        <div class="mt-3 flex items-center gap-2 border-t border-line pt-3 text-xs">
          <button type="button" class="rounded-lg px-2 py-1.5 font-medium text-brand hover:bg-brand-soft" @click="pickNow">{{ time ? t('controls.now') : t('controls.today') }}</button>
          <button v-if="value && !required" type="button" class="rounded-lg px-2 py-1.5 text-muted hover:bg-canvas hover:text-ink" @click="clear">{{ t('controls.clear') }}</button>
          <button v-if="time" type="button" class="ml-auto rounded-lg bg-brand px-3 py-1.5 font-medium text-white hover:bg-[#534bec]" @click="close(true)">{{ t('controls.done') }}</button>
        </div>
      </div>
    </Teleport>
  </div>
</template>

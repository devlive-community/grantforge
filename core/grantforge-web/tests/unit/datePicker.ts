// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

import { flushPromises, type VueWrapper } from '@vue/test-utils'
import UiDatePicker from '@/components/UiDatePicker.vue'

/** The date pickers of a mounted view, also those in its dialogs, by label. */
export function datePicker(wrapper: VueWrapper, label: string) {
  const picker = wrapper.findAllComponents(UiDatePicker).find(found => found.props('label') === label)
  if (!picker) throw new Error(`no date picker labelled ${label}`)
  return picker
}

/** Sets a date picker's value as picking it in the calendar does; the picker's own tests cover the calendar. */
export async function setDate(wrapper: VueWrapper, label: string, value: string): Promise<void> {
  datePicker(wrapper, label).vm.$emit('update:modelValue', value)
  await flushPromises()
}

// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

import { describe, it, expect } from 'vitest'
import { defineComponent, h, nextTick, ref, type PropType, type Ref } from 'vue'
import { mount } from '@vue/test-utils'
import { useFieldErrors, type FieldErrors } from './fieldErrors'

/** A guest that hands the composable whatever source and check a case needs, so one definition serves them all. */
const Guest = defineComponent({
  props: {
    source: { type: Function as PropType<() => unknown>, required: true },
    problems: { type: Function as PropType<() => FieldErrors>, required: true },
  },
  setup: props => useFieldErrors(props.source, props.problems),
  render() { return h('div') },
})

/** A form of one name and one code, checked the way a role form checks them. */
function mountForm(form: Ref<{ name: string; code: string }>) {
  return mount(Guest, { props: {
    source: () => form.value,
    problems: () => {
      const found: FieldErrors = {}
      if (!form.value.name.trim()) found.name = 'enterName'
      if (!form.value.code.trim()) found.code = 'enterCode'
      return found
    },
  } }).vm
}

describe('field errors', () => {
  it('marks every failed field on one submit', () => {
    const vm = mountForm(ref({ name: '', code: '' }))
    expect(vm.invalid()).toBe(true)
    expect(vm.errors).toEqual({ name: 'enterName', code: 'enterCode' })
  })

  it('clears a message once its own field is acceptable, without a submit', async () => {
    const form = ref({ name: '', code: '' })
    const vm = mountForm(form)
    vm.invalid()
    form.value.name = 'Auditor'
    await nextTick()
    expect(vm.errors).toEqual({ code: 'enterCode' })
    form.value.code = 'auditor'
    await nextTick()
    expect(vm.errors).toEqual({})
  })

  it('never adds a message on its own', async () => {
    const form = ref({ name: '', code: '' })
    const vm = mountForm(form)
    form.value.name = ' '
    await nextTick()
    expect(vm.errors).toEqual({})
  })

  it('notices a nested edit', async () => {
    const form = ref({ ldap: { url: '' } })
    const vm = mount(Guest, { props: {
      source: () => form.value,
      problems: (): FieldErrors => (form.value.ldap.url.trim() ? {} : { 'ldap.url': 'enterDirectory' }),
    } }).vm
    vm.invalid()
    expect(vm.errors).toEqual({ 'ldap.url': 'enterDirectory' })
    form.value.ldap.url = 'ldaps://ldap.example.com'
    await nextTick()
    expect(vm.errors).toEqual({})
  })
})

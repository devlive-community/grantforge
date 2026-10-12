// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

import { describe, it, expect } from 'vitest'
import { defineComponent, h, nextTick, ref, type PropType, type Ref } from 'vue'
import { mount } from '@vue/test-utils'
import { ApiError } from './api'
import { useFieldErrors, type FieldErrors } from './fieldErrors'

/** A guest that hands the composable whatever source and check a case needs, so one definition serves them all. */
const Guest = defineComponent({
  props: {
    source: { type: Function as PropType<() => unknown>, required: true },
    problems: { type: Function as PropType<() => FieldErrors>, required: true },
    valueOf: { type: Function as PropType<(field: string) => unknown>, default: undefined },
  },
  setup: props => useFieldErrors(props.source, props.problems, props.valueOf),
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

  describe('refused by the server', () => {
    const refusal = (...fields: string[]) => new ApiError('请求参数不正确。', 400, 0, null,
      { title: 'bad', status: 400, errors: fields.map(field => ({ field, message: `bad ${field}` })) })
    /** A form the server checks alone, which knows each field's value. */
    const serverForm = (form: Ref<{ name: string; code: string }>) => mount(Guest, { props: {
      source: () => form.value, problems: () => ({}), valueOf: (field: string) => form.value[field as 'name' | 'code'] } }).vm

    it('marks what the server refused, each until its own field changes', async () => {
      const form = ref({ name: 'auditor', code: 'auditor' })
      const vm = serverForm(form)
      expect(vm.reject(refusal('name', 'code'))).toBe(true)
      expect(vm.errors).toEqual({ name: 'bad name', code: 'bad code' })
      // Editing one field leaves the other's message standing.
      form.value.name = 'auditors'
      await nextTick()
      expect(vm.errors).toEqual({ code: 'bad code' })
      form.value.code = 'audit'
      await nextTick()
      expect(vm.errors).toEqual({})
    })

    it('leaves a refusal of nothing in particular to the form', () => {
      const vm = serverForm(ref({ name: '', code: '' }))
      expect(vm.reject(new ApiError('服务暂时不可用。', 503))).toBe(false)
      expect(vm.reject(new Error('offline'))).toBe(false)
      expect(vm.errors).toEqual({})
    })

    it('names the fields the way the form does', () => {
      const vm = serverForm(ref({ name: '', code: '' }))
      vm.reject(refusal('settings.code[0]'), field => field.replace(/^settings\./, '').replace(/\[\d+\]$/, ''))
      expect(vm.errors).toEqual({ code: 'bad settings.code[0]' })
    })
  })
})

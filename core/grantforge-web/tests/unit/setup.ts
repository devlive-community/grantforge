// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

// Global setup for Vitest (see vitest.config.ts).

// jsdom does not implement the modal dialog API; model it with the open attribute.
HTMLDialogElement.prototype.showModal ??= function (this: HTMLDialogElement) { this.setAttribute('open', '') }
HTMLDialogElement.prototype.close ??= function (this: HTMLDialogElement) {
  this.removeAttribute('open')
  this.dispatchEvent(new Event('close'))
}

// Components translate through vue-i18n; tests run in Simplified Chinese unless they switch explicitly.
import { config } from '@vue/test-utils'
import { i18n, setLocale } from '@/i18n'

setLocale('zh-CN')
config.global.plugins.push(i18n)

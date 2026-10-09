// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

import type { Locale } from '../index'

/**
 * The name of every interface language, in the language itself, for the picker. It sits beside the dictionaries
 * because a language name is not a message: the console must show "Deutsch" to a German reader whatever the
 * interface language currently is.
 */
export const LOCALE_LABELS: Record<Locale, string> = {
  'zh-CN': '中文',
  'en-US': 'English',
}

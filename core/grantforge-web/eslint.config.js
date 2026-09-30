// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

import js from '@eslint/js'
import vue from 'eslint-plugin-vue'
import ts from 'typescript-eslint'
import globals from 'globals'

export default [
  // src/api/schema.d.ts is generated from the OpenAPI contract (pnpm api:generate).
  { ignores: ['dist/**', 'node_modules/**', 'node/**', 'test-results/**', 'playwright-report/**', 'src/api/schema.d.ts'] },
  js.configs.recommended,
  ...ts.configs.recommended,
  ...vue.configs['flat/recommended'],
  { files: ['**/*.{ts,vue,js}'], languageOptions: { globals: { ...globals.browser, ...globals.node } } },
  { files: ['**/*.vue'], languageOptions: { parserOptions: { parser: ts.parser } } },
  // Null safety: no non-null assertions; handle undefined explicitly.
  { rules: { '@typescript-eslint/no-non-null-assertion': 'error' } },
  { rules: { 'vue/multi-word-component-names': 'error', 'vue/html-self-closing': ['error', { html: { void: 'always', normal: 'never', component: 'always' } }], 'vue/max-attributes-per-line': ['error', { singleline: 4 }], 'vue/singleline-html-element-content-newline': 'off' } },
]

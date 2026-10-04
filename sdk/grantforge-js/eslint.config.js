// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

import js from '@eslint/js'
import ts from 'typescript-eslint'
import globals from 'globals'

export default [
  { ignores: ['dist/**', 'node_modules/**'] },
  js.configs.recommended,
  ...ts.configs.recommended,
  { files: ['**/*.{ts,js}'], languageOptions: { globals: { ...globals.browser, ...globals.node } } },
  // Null safety: no non-null assertions; handle undefined explicitly.
  { rules: { '@typescript-eslint/no-non-null-assertion': 'error' } },
]

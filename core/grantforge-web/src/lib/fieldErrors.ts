// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

import { ref, watch } from 'vue'

/** The messages a form's fields carry, keyed by the field each one belongs to. */
export type FieldErrors = Record<string, string>

/**
 * The failed fields of one form. `problems` runs every check, so a single submit marks each of them instead of
 * stopping at the first, and a message goes away as soon as its own field is acceptable again — without waiting for
 * the next submit. Messages only ever go away on their own: a field nobody has submitted yet stays silent until
 * somebody does.
 *
 * `source` answers what the form is made of, so the composable notices an edit; pass the form itself, or the handful
 * of values the checks read.
 */
export function useFieldErrors(source: () => unknown, problems: () => FieldErrors) {
  const errors = ref<FieldErrors>({})
  watch(source, () => {
    if (!Object.keys(errors.value).length) return
    const still = problems()
    for (const field of Object.keys(errors.value)) if (!still[field]) delete errors.value[field]
  }, { deep: true })
  return {
    errors,
    /** Runs every check and keeps what fails. Answers whether the form has anything left to fix. */
    invalid(): boolean {
      const found = problems()
      errors.value = found
      return Object.keys(found).length > 0
    },
  }
}

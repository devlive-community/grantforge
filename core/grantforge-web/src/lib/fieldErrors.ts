// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

import { ref, watch } from 'vue'
import { ApiError } from '@/lib/api'

/** The messages a form's fields carry, keyed by the field each one belongs to. */
export type FieldErrors = Record<string, string>

/** How a field's value is read, so a message the server gave it can go once the field is edited. */
export type FieldValue = (field: string) => unknown

/**
 * The failed fields of one form. `problems` runs every check, so a single submit marks each of them instead of
 * stopping at the first, and a message goes away as soon as its own field is acceptable again — without waiting for
 * the next submit. Messages only ever go away on their own: a field nobody has submitted yet stays silent until
 * somebody does.
 *
 * The server checks the form again, and `reject` puts what it refused on the fields as well. Such a message stays
 * while other fields are edited and goes once its own field changes; with `valueOf` the composable can tell, and
 * without it any edit of the form counts.
 *
 * `source` answers what the form is made of, so the composable notices an edit; pass the form itself, or the handful
 * of values the checks read.
 */
export function useFieldErrors(source: () => unknown, problems: () => FieldErrors = () => ({}), valueOf?: FieldValue) {
  const errors = ref<FieldErrors>({})
  // What each field the server refused held then, so its message goes once the field holds anything else.
  const refused = new Map<string, string>()
  const snapshot = (field: string) => JSON.stringify(valueOf ? valueOf(field) : source()) ?? ''
  watch(source, () => {
    if (!Object.keys(errors.value).length) return
    const still = problems()
    for (const field of Object.keys(errors.value)) {
      if (refused.has(field)) {
        if (snapshot(field) === refused.get(field)) continue
        refused.delete(field)
        // Edited since: the server's word no longer stands, though the form's own checks may still have one.
        const local = still[field]
        if (local) errors.value[field] = local; else delete errors.value[field]
        continue
      }
      if (!still[field]) delete errors.value[field]
    }
  }, { deep: true })
  return {
    errors,
    /** Runs every check and keeps what fails. Answers whether the form has anything left to fix. */
    invalid(): boolean {
      refused.clear()
      const found = problems()
      errors.value = found
      return Object.keys(found).length > 0
    },
    /**
     * Puts the fields the server refused on their fields, each as the server worded it. Answers whether it marked any,
     * in which case the form needs no message of its own besides; a refusal of anything else is the form's to show.
     *
     * @param fieldOf maps a field the server names, such as `settings.urls[0]`, to the form's own name for it
     */
    reject(reason: unknown, fieldOf: (field: string) => string = field => field): boolean {
      const issues = reason instanceof ApiError ? reason.problem?.errors ?? [] : []
      if (!issues.length) return false
      const found: FieldErrors = {}
      for (const issue of issues) found[fieldOf(issue.field)] ??= issue.message
      for (const field of Object.keys(found)) refused.set(field, snapshot(field))
      errors.value = { ...errors.value, ...found }
      return true
    },
  }
}

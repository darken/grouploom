import type { ApiError } from '@/api/errors'

/** Maps a validation ProblemDetail's `errors[]` to `{ field: message }` (first message per field). */
export function fieldErrorsFrom(error: ApiError | null): Record<string, string> {
  const result: Record<string, string> = {}
  for (const item of error?.problem.errors ?? []) {
    if (item.field && item.message && !(item.field in result)) result[item.field] = item.message
  }
  return result
}

/** True for errors that belong in the form-level banner rather than under a field. */
export function isFormLevel(error: ApiError | null): boolean {
  return error !== null && !error.problem.errors?.length
}

import type { components } from './schema'

export type ProblemDetail = components['schemas']['ProblemDetail']

export class ApiError extends Error {
  readonly problem: ProblemDetail
  readonly status: number

  constructor(problem: ProblemDetail, status = problem.status ?? 0) {
    super(problem.detail ?? problem.title ?? 'Request failed')
    this.name = 'ApiError'
    this.problem = problem
    this.status = status
  }
}

interface ApiResult<T> {
  data?: T
  error?: unknown
  response: Response
}

function isProblem(value: unknown): value is ProblemDetail {
  return typeof value === 'object' && value !== null
}

export function unwrap<T>({ data, error, response }: ApiResult<T>): T {
  if (error !== undefined || data === undefined) {
    const problem = isProblem(error) ? error : { status: response.status, title: response.statusText }
    throw new ApiError(problem, response.status)
  }
  return data
}

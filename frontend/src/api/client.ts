import createClient from 'openapi-fetch'
import type { paths } from './schema'
import { authMiddleware } from './auth-middleware'
import { wakeMiddleware } from './wake-middleware'

export const api = createClient<paths>({ baseUrl: '/api' })

// Responses run through middleware in reverse, so the wake retry happens before the auth 401 handling.
api.use(authMiddleware, wakeMiddleware)

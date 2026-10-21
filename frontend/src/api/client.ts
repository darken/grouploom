import createClient from 'openapi-fetch'
import type { paths } from './schema'
import { authMiddleware } from './auth-middleware'

export const api = createClient<paths>({ baseUrl: '/api' })

api.use(authMiddleware)

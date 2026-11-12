export class ApiError extends Error {
  status: number

  constructor(status: number, message: string) {
    super(message)
    this.status = status
  }
}

let csrfToken = ''
let csrfHeaderName = 'X-XSRF-TOKEN'
let unauthorizedHandler: (() => void) | null = null

export function onUnauthorized(handler: () => void) {
  unauthorizedHandler = handler
}

export async function ensureCsrf() {
  const response = await fetch('/api/auth/csrf', { credentials: 'include' })
  if (!response.ok) {
    throw new ApiError(response.status, '无法获取安全令牌')
  }
  const data = (await response.json()) as { token: string; headerName: string }
  csrfToken = data.token
  csrfHeaderName = data.headerName || 'X-XSRF-TOKEN'
}

function isWrite(method: string) {
  return !['GET', 'HEAD', 'OPTIONS'].includes(method.toUpperCase())
}

async function readError(response: Response): Promise<ApiError> {
  try {
    const body = (await response.json()) as { message?: string }
    return new ApiError(response.status, body.message || '请求失败')
  } catch {
    return new ApiError(response.status, '请求失败')
  }
}

export async function request<T>(path: string, init: RequestInit = {}): Promise<T> {
  const method = (init.method || 'GET').toUpperCase()
  if (isWrite(method) && !csrfToken) {
    await ensureCsrf()
  }

  const headers = new Headers(init.headers)
  if (init.body && !headers.has('Content-Type')) {
    headers.set('Content-Type', 'application/json')
  }
  if (isWrite(method) && csrfToken) {
    headers.set(csrfHeaderName, csrfToken)
  }

  const response = await fetch(path, {
    ...init,
    method,
    headers,
    credentials: 'include',
  })

  if (response.status === 401) {
    unauthorizedHandler?.()
    throw await readError(response)
  }

  if (!response.ok) {
    throw await readError(response)
  }

  if (response.status === 204) {
    return undefined as T
  }

  const text = await response.text()
  if (!text) {
    return undefined as T
  }
  return JSON.parse(text) as T
}

export function get<T>(path: string) {
  return request<T>(path)
}

export function post<T>(path: string, body?: unknown) {
  return request<T>(path, { method: 'POST', body: body === undefined ? undefined : JSON.stringify(body) })
}

export function patch<T>(path: string, body?: unknown) {
  return request<T>(path, { method: 'PATCH', body: body === undefined ? undefined : JSON.stringify(body) })
}

export function put<T>(path: string, body?: unknown) {
  return request<T>(path, { method: 'PUT', body: body === undefined ? undefined : JSON.stringify(body) })
}

export function del<T>(path: string) {
  return request<T>(path, { method: 'DELETE' })
}

export function resetCsrf() {
  csrfToken = ''
}

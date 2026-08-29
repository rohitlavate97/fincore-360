export interface ApiError {
  errorCode: string
  message: string
  details?: Array<{ field: string; issue: string }>
  traceId?: string
  status?: number
}

let refreshPromise: Promise<string> | null = null

/**
 * Single-flight token refresh promise gate (H-3).
 * Collapses concurrent 401s into a single POST /api/v1/auth/refresh call,
 * preventing refresh token reuse detection trips (FM-BACKEND-005).
 */
export async function getRefreshedToken(): Promise<string> {
  if (!refreshPromise) {
    refreshPromise = (async () => {
      try {
        const stored = sessionStorage.getItem('fincore_session')
        if (!stored) throw new Error('No active session')
        const session = JSON.parse(stored)
        if (!session.refreshToken) throw new Error('No refresh token')

        const res = await fetch('/api/v1/auth/refresh', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ refreshToken: session.refreshToken }),
        })

        if (!res.ok) {
          sessionStorage.removeItem('fincore_session')
          if (typeof window !== 'undefined') {
            window.dispatchEvent(new CustomEvent('fincore:auth:expired'))
          }
          throw new Error('Refresh token rejected or expired')
        }

        const data = await res.json()
        session.accessToken = data.accessToken
        session.refreshToken = data.refreshToken
        sessionStorage.setItem('fincore_session', JSON.stringify(session))
        apiClient.setToken(data.accessToken)
        return data.accessToken as string
      } finally {
        refreshPromise = null
      }
    })()
  }
  return refreshPromise
}

class ApiClient {
  private token: string | null = null

  setToken(token: string | null) {
    this.token = token
  }

  getToken(): string | null {
    return this.token
  }

  private generateCorrelationId(): string {
    if (typeof crypto !== 'undefined' && crypto.randomUUID) {
      return crypto.randomUUID()
    }
    return 'web-' + Math.random().toString(36).substring(2, 15)
  }

  async request<T>(
    endpoint: string,
    options: RequestInit = {},
    isRetry = false
  ): Promise<T> {
    const headers = new Headers(options.headers || {})

    if (!headers.has('Content-Type') && !(options.body instanceof FormData)) {
      headers.set('Content-Type', 'application/json')
    }

    if (!headers.has('X-Correlation-ID')) {
      headers.set('X-Correlation-ID', this.generateCorrelationId())
    }

    if (this.token && !headers.has('Authorization')) {
      headers.set('Authorization', `Bearer ${this.token}`)
    }

    const response = await fetch(endpoint, {
      ...options,
      headers,
    })

    // Single-flight 401 handling: refresh token and replay original request once (H-3)
    if (response.status === 401 && !isRetry && !endpoint.includes('/api/v1/auth/')) {
      try {
        const newAccessToken = await getRefreshedToken()
        headers.set('Authorization', `Bearer ${newAccessToken}`)
        return await this.request<T>(endpoint, { ...options, headers }, true)
      } catch {
        // Refresh failed, continue to throw original 401
      }
    }

    if (!response.ok) {
      let errorBody: ApiError
      try {
        errorBody = await response.json()
      } catch {
        errorBody = {
          errorCode: 'UNKNOWN_ERROR',
          message: `Request failed with status ${response.status}`,
        }
      }
      errorBody.status = response.status
      throw errorBody
    }

    if (response.status === 204) {
      return {} as T
    }

    return response.json()
  }

  get<T>(endpoint: string, headers?: Record<string, string>): Promise<T> {
    return this.request<T>(endpoint, { method: 'GET', headers })
  }

  post<T>(endpoint: string, body?: unknown, headers?: Record<string, string>): Promise<T> {
    return this.request<T>(endpoint, {
      method: 'POST',
      headers,
      body: body ? JSON.stringify(body) : undefined,
    })
  }

  patch<T>(endpoint: string, body?: unknown, headers?: Record<string, string>): Promise<T> {
    return this.request<T>(endpoint, {
      method: 'PATCH',
      headers,
      body: body ? JSON.stringify(body) : undefined,
    })
  }
}

export const apiClient = new ApiClient()

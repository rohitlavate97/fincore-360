export interface ApiError {
  errorCode: string
  message: string
  details?: Array<{ field: string; issue: string }>
  traceId?: string
  status?: number
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
    options: RequestInit = {}
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

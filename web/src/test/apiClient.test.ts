import { describe, it, expect, beforeEach, vi } from 'vitest'
import { apiClient } from '../services/apiClient'

describe('ApiClient', () => {
  beforeEach(() => {
    apiClient.setToken(null)
    vi.restoreAllMocks()
  })

  it('attaches X-Correlation-ID and Content-Type automatically', async () => {
    let capturedHeaders: Headers | null = null

    global.fetch = vi.fn().mockImplementation((_url, init) => {
      capturedHeaders = new Headers(init.headers)
      return Promise.resolve({
        ok: true,
        status: 200,
        json: () => Promise.resolve({ success: true }),
      })
    })

    const res = await apiClient.get<{ success: boolean }>('/api/v1/test')

    expect(res.success).toBe(true)
    expect(capturedHeaders).not.toBeNull()
    expect((capturedHeaders as unknown as Headers).has('X-Correlation-ID')).toBe(true)
    expect((capturedHeaders as unknown as Headers).get('Content-Type')).toBe('application/json')
  })

  it('attaches Bearer token when token is set in ApiClient', async () => {
    let capturedHeaders: Headers | null = null

    global.fetch = vi.fn().mockImplementation((_url, init) => {
      capturedHeaders = new Headers(init.headers)
      return Promise.resolve({
        ok: true,
        status: 200,
        json: () => Promise.resolve({ data: 'ok' }),
      })
    })

    apiClient.setToken('mock-jwt-token-123')
    await apiClient.get('/api/v1/secure')

    expect((capturedHeaders as unknown as Headers).get('Authorization')).toBe('Bearer mock-jwt-token-123')
  })

  it('throws ApiError with error contract on 403 Forbidden', async () => {
    global.fetch = vi.fn().mockImplementation(() => {
      return Promise.resolve({
        ok: false,
        status: 403,
        json: () =>
          Promise.resolve({
            errorCode: 'ACCESS_DENIED',
            message: 'User does not possess ROLE_ADMIN',
            traceId: 'trace-403',
          }),
      })
    })

    await expect(apiClient.get('/api/v1/admin/restricted')).rejects.toMatchObject({
      status: 403,
      errorCode: 'ACCESS_DENIED',
      message: 'User does not possess ROLE_ADMIN',
    })
  })

  it('single-flights 401 token refresh across concurrent requests without duplicate refreshes (H-3)', async () => {
    let refreshCalls = 0
    sessionStorage.setItem(
      'fincore_session',
      JSON.stringify({
        accessToken: 'expired-token',
        refreshToken: 'valid-refresh-token',
        userId: 'user-1',
        username: 'alice',
        roles: ['CUSTOMER'],
      })
    )
    apiClient.setToken('expired-token')

    global.fetch = vi.fn().mockImplementation((url: string, init?: RequestInit) => {
      if (url === '/api/v1/auth/refresh') {
        refreshCalls++
        return Promise.resolve({
          ok: true,
          status: 200,
          json: () =>
            Promise.resolve({
              accessToken: 'fresh-access-token',
              refreshToken: 'fresh-refresh-token',
            }),
        })
      }

      const headers = new Headers(init?.headers)
      const auth = headers.get('Authorization')

      // First attempt with expired token fails with 401
      if (auth === 'Bearer expired-token') {
        return Promise.resolve({
          ok: false,
          status: 401,
          json: () => Promise.resolve({ errorCode: 'AUTHENTICATION_REQUIRED', message: 'Expired' }),
        })
      }

      // Replayed attempt with fresh token succeeds
      if (auth === 'Bearer fresh-access-token') {
        return Promise.resolve({
          ok: true,
          status: 200,
          json: () => Promise.resolve({ data: 'replayed-success' }),
        })
      }

      return Promise.resolve({ ok: false, status: 500 })
    })

    // Three concurrent requests hit 401 simultaneously
    const [res1, res2, res3] = await Promise.all([
      apiClient.get<{ data: string }>('/api/v1/accounts'),
      apiClient.get<{ data: string }>('/api/v1/transfers'),
      apiClient.get<{ data: string }>('/api/v1/transactions'),
    ])

    expect(res1.data).toBe('replayed-success')
    expect(res2.data).toBe('replayed-success')
    expect(res3.data).toBe('replayed-success')
    expect(refreshCalls).toBe(1)
  })
})


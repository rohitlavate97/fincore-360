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
})

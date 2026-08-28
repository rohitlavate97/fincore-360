import React from 'react'
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { App } from '../App'
import { Role } from '../types/auth'
import { apiClient } from '../services/apiClient'

function setupSession(role: Role) {
  sessionStorage.setItem(
    'fincore_session',
    JSON.stringify({
      userId: `user-${role.toLowerCase()}`,
      username: `${role.toLowerCase()}_tester`,
      roles: [role],
      accessToken: `token-${role}`,
    })
  )
}

describe('Phase 9 Exit Criterion: Each role sees only permitted screens', () => {
  beforeEach(() => {
    sessionStorage.clear()
    vi.restoreAllMocks()
    // Mock API responses so pages don't fail when mounted
    vi.spyOn(apiClient, 'get').mockResolvedValue({ items: [] })
  })

  // 1. CUSTOMER: Permitted: /accounts, /transfer; Forbidden: /transactions, /audit
  it('CUSTOMER: can access /accounts and /transfer, but receives 403 Access Denied on /audit and /transactions', async () => {
    setupSession('CUSTOMER')

    // Access permitted: /accounts
    const { unmount: unmount1 } = render(
      <MemoryRouter initialEntries={['/accounts']}>
        <App />
      </MemoryRouter>
    )
    expect(await screen.findByTestId('accounts-page')).toBeInTheDocument()
    expect(screen.queryByTestId('access-denied-view')).not.toBeInTheDocument()
    unmount1()

    // Access permitted: /transfer
    const { unmount: unmount2 } = render(
      <MemoryRouter initialEntries={['/transfer']}>
        <App />
      </MemoryRouter>
    )
    expect(await screen.findByTestId('transfer-page')).toBeInTheDocument()
    expect(screen.queryByTestId('access-denied-view')).not.toBeInTheDocument()
    unmount2()

    // Access forbidden: /audit -> 403
    const { unmount: unmount3 } = render(
      <MemoryRouter initialEntries={['/audit']}>
        <App />
      </MemoryRouter>
    )
    expect(await screen.findByTestId('access-denied-view')).toBeInTheDocument()
    expect(screen.queryByTestId('audit-page')).not.toBeInTheDocument()
    unmount3()

    // Access forbidden: /transactions -> 403
    const { unmount: unmount4 } = render(
      <MemoryRouter initialEntries={['/transactions']}>
        <App />
      </MemoryRouter>
    )
    expect(await screen.findByTestId('access-denied-view')).toBeInTheDocument()
    expect(screen.queryByTestId('transactions-page')).not.toBeInTheDocument()
    unmount4()
  })

  // 2. AUDITOR: Permitted: /audit; Forbidden: /transfer, /transactions
  it('AUDITOR: can access /audit, but receives 403 Access Denied on /transfer and /transactions', async () => {
    setupSession('AUDITOR')

    // Permitted: /audit
    const { unmount: unmount1 } = render(
      <MemoryRouter initialEntries={['/audit']}>
        <App />
      </MemoryRouter>
    )
    expect(await screen.findByTestId('audit-page')).toBeInTheDocument()
    expect(screen.queryByTestId('access-denied-view')).not.toBeInTheDocument()
    unmount1()

    // Forbidden: /transfer -> 403
    const { unmount: unmount2 } = render(
      <MemoryRouter initialEntries={['/transfer']}>
        <App />
      </MemoryRouter>
    )
    expect(await screen.findByTestId('access-denied-view')).toBeInTheDocument()
    expect(screen.queryByTestId('transfer-page')).not.toBeInTheDocument()
    unmount2()

    // Forbidden: /transactions -> 403
    const { unmount: unmount3 } = render(
      <MemoryRouter initialEntries={['/transactions']}>
        <App />
      </MemoryRouter>
    )
    expect(await screen.findByTestId('access-denied-view')).toBeInTheDocument()
    expect(screen.queryByTestId('transactions-page')).not.toBeInTheDocument()
    unmount3()
  })

  // 3. OPERATIONS: Permitted: /transactions; Forbidden: /audit, /transfer
  it('OPERATIONS: can access /transactions, but receives 403 Access Denied on /audit and /transfer', async () => {
    setupSession('OPERATIONS')

    // Permitted: /transactions
    const { unmount: unmount1 } = render(
      <MemoryRouter initialEntries={['/transactions']}>
        <App />
      </MemoryRouter>
    )
    expect(await screen.findByTestId('transactions-page')).toBeInTheDocument()
    expect(screen.queryByTestId('access-denied-view')).not.toBeInTheDocument()
    unmount1()

    // Forbidden: /audit -> 403
    const { unmount: unmount2 } = render(
      <MemoryRouter initialEntries={['/audit']}>
        <App />
      </MemoryRouter>
    )
    expect(await screen.findByTestId('access-denied-view')).toBeInTheDocument()
    expect(screen.queryByTestId('audit-page')).not.toBeInTheDocument()
    unmount2()

    // Forbidden: /transfer -> 403
    const { unmount: unmount3 } = render(
      <MemoryRouter initialEntries={['/transfer']}>
        <App />
      </MemoryRouter>
    )
    expect(await screen.findByTestId('access-denied-view')).toBeInTheDocument()
    expect(screen.queryByTestId('transfer-page')).not.toBeInTheDocument()
    unmount3()
  })

  // 4. ADMIN: Permitted to access ALL screens
  it('ADMIN: can access all banking screens without restriction', async () => {
    setupSession('ADMIN')

    const routes = ['/accounts', '/transfer', '/transactions', '/audit']
    for (const route of routes) {
      const { unmount } = render(
        <MemoryRouter initialEntries={[route]}>
          <App />
        </MemoryRouter>
      )
      expect(screen.queryByTestId('access-denied-view')).not.toBeInTheDocument()
      unmount()
    }
  })
})

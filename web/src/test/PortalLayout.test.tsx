import { describe, it, expect } from 'vitest'
import { render, screen } from '@testing-library/react'
import { MemoryRouter, Routes, Route } from 'react-router-dom'
import { AuthProvider } from '../context/AuthContext'
import { PortalLayout } from '../components/layout/PortalLayout'
import { Role, UserSession } from '../types/auth'

const createMockSession = (role: Role): UserSession => ({
  userId: `user-${role.toLowerCase()}`,
  username: `${role.toLowerCase()}_user`,
  roles: [role],
  customerId: `cust-${role.toLowerCase()}`,
  accessToken: `mock-token-${role}`,
  refreshToken: `mock-refresh-${role}`,
})

describe('PortalLayout Dynamic Role Navigation Filtering', () => {
  it('displays only Auditor-permitted navigation items for AUDITOR role', async () => {
    render(
      <MemoryRouter initialEntries={['/']}>
        <AuthProvider initialSession={createMockSession('AUDITOR')}>
          <Routes>
            <Route element={<PortalLayout />}>
              <Route path="/" element={<div>Dashboard</div>} />
            </Route>
          </Routes>
        </AuthProvider>
      </MemoryRouter>
    )

    expect(await screen.findByTestId('nav-item-overview-dashboard')).toBeInTheDocument()
    expect(screen.getByTestId('nav-item-audit-log-explorer')).toBeInTheDocument()

    // Transaction Monitor & Transfer are NOT permitted for AUDITOR
    expect(screen.queryByTestId('nav-item-transaction-monitor')).not.toBeInTheDocument()
    expect(screen.queryByTestId('nav-item-transfer-money')).not.toBeInTheDocument()
  })

  it('displays only Operations-permitted navigation items for OPERATIONS role', async () => {
    render(
      <MemoryRouter initialEntries={['/']}>
        <AuthProvider initialSession={createMockSession('OPERATIONS')}>
          <Routes>
            <Route element={<PortalLayout />}>
              <Route path="/" element={<div>Dashboard</div>} />
            </Route>
          </Routes>
        </AuthProvider>
      </MemoryRouter>
    )

    expect(await screen.findByTestId('nav-item-overview-dashboard')).toBeInTheDocument()
    expect(screen.getByTestId('nav-item-transaction-monitor')).toBeInTheDocument()

    // Audit Log Explorer is NOT permitted for OPERATIONS
    expect(screen.queryByTestId('nav-item-audit-log-explorer')).not.toBeInTheDocument()
  })

  it('displays all navigation items for ADMIN role', async () => {
    render(
      <MemoryRouter initialEntries={['/']}>
        <AuthProvider initialSession={createMockSession('ADMIN')}>
          <Routes>
            <Route element={<PortalLayout />}>
              <Route path="/" element={<div>Dashboard</div>} />
            </Route>
          </Routes>
        </AuthProvider>
      </MemoryRouter>
    )

    expect(await screen.findByTestId('nav-item-overview-dashboard')).toBeInTheDocument()
    expect(screen.getByTestId('nav-item-accounts-explorer')).toBeInTheDocument()
    expect(screen.getByTestId('nav-item-transfer-money')).toBeInTheDocument()
    expect(screen.getByTestId('nav-item-transaction-monitor')).toBeInTheDocument()
    expect(screen.getByTestId('nav-item-audit-log-explorer')).toBeInTheDocument()
  })
})

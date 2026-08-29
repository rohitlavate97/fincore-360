import { describe, it, expect } from 'vitest'
import { render, screen } from '@testing-library/react'
import { MemoryRouter, Routes, Route } from 'react-router-dom'
import { AuthProvider } from '../context/AuthContext'
import { ProtectedRoute } from '../components/auth/ProtectedRoute'
import { Role, UserSession } from '../types/auth'

const createMockSession = (role: Role): UserSession => ({
  userId: `user-${role.toLowerCase()}`,
  username: `${role.toLowerCase()}_user`,
  roles: [role],
  customerId: `cust-${role.toLowerCase()}`,
  accessToken: `mock-token-${role}`,
  refreshToken: `mock-refresh-${role}`,
})

describe('ProtectedRoute & RBAC Enforcement', () => {
  it('redirects unauthenticated users to /login', () => {
    render(
      <MemoryRouter initialEntries={['/admin']}>
        <AuthProvider>
          <Routes>
            <Route path="/login" element={<div>Login Page Screen</div>} />
            <Route
              path="/admin"
              element={
                <ProtectedRoute allowedRoles={['ADMIN']}>
                  <div>Admin Secure Area</div>
                </ProtectedRoute>
              }
            />
          </Routes>
        </AuthProvider>
      </MemoryRouter>
    )

    expect(screen.getByText('Login Page Screen')).toBeInTheDocument()
    expect(screen.queryByText('Admin Secure Area')).not.toBeInTheDocument()
  })

  it('permits authorized role to view screen', async () => {
    render(
      <MemoryRouter initialEntries={['/portal']}>
        <AuthProvider initialSession={createMockSession('ADMIN')}>
          <Routes>
            <Route
              path="/portal"
              element={
                <ProtectedRoute allowedRoles={['ADMIN']}>
                  <div>Admin Secure Area</div>
                </ProtectedRoute>
              }
            />
          </Routes>
        </AuthProvider>
      </MemoryRouter>
    )

    expect(await screen.findByText('Admin Secure Area')).toBeInTheDocument()
  })

  it('blocks unauthorized role with 403 Access Denied screen', async () => {
    render(
      <MemoryRouter initialEntries={['/audit-trail']}>
        <AuthProvider initialSession={createMockSession('CUSTOMER')}>
          <Routes>
            <Route
              path="/audit-trail"
              element={
                <ProtectedRoute allowedRoles={['AUDITOR', 'ADMIN']}>
                  <div>Audit Log Confidential</div>
                </ProtectedRoute>
              }
            />
          </Routes>
        </AuthProvider>
      </MemoryRouter>
    )

    expect(await screen.findByTestId('access-denied-view')).toBeInTheDocument()
    expect(screen.getByText(/403 — Access Denied/i)).toBeInTheDocument()
    expect(screen.queryByText('Audit Log Confidential')).not.toBeInTheDocument()
  })
})

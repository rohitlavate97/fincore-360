import React from 'react'
import { describe, it, expect } from 'vitest'
import { render, screen } from '@testing-library/react'
import { MemoryRouter, Routes, Route } from 'react-router-dom'
import { AuthProvider, useAuth } from '../context/AuthContext'
import { ProtectedRoute } from '../components/auth/ProtectedRoute'
import { Role } from '../types/auth'

const TestConsumer: React.FC<{ targetRole?: Role }> = ({ targetRole }) => {
  const { mockLoginAs } = useAuth()
  const calledRef = React.useRef(false)

  React.useEffect(() => {
    if (targetRole && !calledRef.current) {
      calledRef.current = true
      mockLoginAs(targetRole)
    }
  }, [targetRole, mockLoginAs])

  return <div>Loaded Test Consumer</div>
}

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
        <AuthProvider>
          <TestConsumer targetRole="ADMIN" />
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
        <AuthProvider>
          <TestConsumer targetRole="CUSTOMER" />
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

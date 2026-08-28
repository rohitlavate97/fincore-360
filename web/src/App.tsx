import React from 'react'
import { Routes, Route, Navigate } from 'react-router-dom'
import { AuthProvider } from './context/AuthContext'
import { ProtectedRoute } from './components/auth/ProtectedRoute'
import { PortalLayout } from './components/layout/PortalLayout'
import { LoginPage } from './pages/LoginPage'
import { DashboardPage } from './pages/DashboardPage'
import { AccountsPage } from './pages/AccountsPage'
import { TransferPage } from './pages/TransferPage'
import { TransactionsPage } from './pages/TransactionsPage'
import { AuditPage } from './pages/AuditPage'
import { ObservabilityPage } from './pages/ObservabilityPage'

export const App: React.FC = () => {
  return (
    <AuthProvider>
      <Routes>
        <Route path="/login" element={<LoginPage />} />

        <Route element={<PortalLayout />}>
          <Route
            path="/"
            element={
              <ProtectedRoute allowedRoles={['CUSTOMER', 'SUPPORT_AGENT', 'OPERATIONS', 'AUDITOR', 'ADMIN']}>
                <DashboardPage />
              </ProtectedRoute>
            }
          />
          <Route
            path="/accounts"
            element={
              <ProtectedRoute allowedRoles={['CUSTOMER', 'SUPPORT_AGENT', 'ADMIN']}>
                <AccountsPage />
              </ProtectedRoute>
            }
          />
          <Route
            path="/transfer"
            element={
              <ProtectedRoute allowedRoles={['CUSTOMER', 'ADMIN']}>
                <TransferPage />
              </ProtectedRoute>
            }
          />
          <Route
            path="/transactions"
            element={
              <ProtectedRoute allowedRoles={['OPERATIONS', 'ADMIN']}>
                <TransactionsPage />
              </ProtectedRoute>
            }
          />
          <Route
            path="/audit"
            element={
              <ProtectedRoute allowedRoles={['AUDITOR', 'ADMIN']}>
                <AuditPage />
              </ProtectedRoute>
            }
          />
          <Route
            path="/observability"
            element={
              <ProtectedRoute allowedRoles={['OPERATIONS', 'AUDITOR', 'ADMIN']}>
                <ObservabilityPage />
              </ProtectedRoute>
            }
          />
        </Route>

        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </AuthProvider>
  )
}

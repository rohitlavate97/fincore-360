import React from 'react'
import { Navigate } from 'react-router-dom'
import { Role } from '../../types/auth'
import { useAuth } from '../../context/AuthContext'
import { AccessDenied } from './AccessDenied'

interface ProtectedRouteProps {
  allowedRoles?: Role[]
  children: React.ReactNode
}

export const ProtectedRoute: React.FC<ProtectedRouteProps> = ({ allowedRoles, children }) => {
  const { user, isAuthenticated, isLoading, hasAnyRole } = useAuth()

  if (isLoading) {
    return <div data-testid="auth-loading" style={{ padding: '2rem', textAlign: 'center' }}>Authenticating...</div>
  }

  if (!isAuthenticated) {
    return <Navigate to="/login" replace />
  }

  if (allowedRoles && allowedRoles.length > 0 && !hasAnyRole(allowedRoles)) {
    return (
      <AccessDenied
        requiredRoles={allowedRoles}
        userRoles={user?.roles || []}
      />
    )
  }

  return <>{children}</>
}

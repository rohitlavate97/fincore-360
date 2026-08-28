import React, { createContext, useContext, useState, useEffect, useCallback } from 'react'
import { Role, UserSession, DecodedToken, AuthResponse } from '../types/auth'
import { apiClient } from '../services/apiClient'

interface AuthContextType {
  user: UserSession | null
  isAuthenticated: boolean
  isLoading: boolean
  login: (username: string, password: string) => Promise<void>
  logout: () => Promise<void>
  hasRole: (role: Role) => boolean
  hasAnyRole: (roles: Role[]) => boolean
  mockLoginAs: (role: Role, username?: string) => void
}

const AuthContext = createContext<AuthContextType | undefined>(undefined)

function parseJwt(token: string): DecodedToken {
  try {
    const base64Url = token.split('.')[1]
    const base64 = base64Url.replace(/-/g, '+').replace(/_/g, '/')
    const jsonPayload = decodeURIComponent(
      atob(base64)
        .split('')
        .map((c) => '%' + ('00' + c.charCodeAt(0).toString(16)).slice(-2))
        .join('')
    )
    return JSON.parse(jsonPayload)
  } catch {
    return { sub: 'unknown', roles: [] }
  }
}

export const AuthProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const [user, setUser] = useState<UserSession | null>(null)
  const [isLoading, setIsLoading] = useState<boolean>(true)

  useEffect(() => {
    const stored = sessionStorage.getItem('fincore_session')
    if (stored) {
      try {
        const parsed: UserSession = JSON.parse(stored)
        setUser(parsed)
        apiClient.setToken(parsed.accessToken)
      } catch {
        sessionStorage.removeItem('fincore_session')
      }
    }
    setIsLoading(false)
  }, [])

  const login = useCallback(async (username: string, password: string): Promise<void> => {
    const response = await apiClient.post<AuthResponse>('/api/v1/auth/login', {
      username,
      password,
    })

    const decoded = parseJwt(response.accessToken)
    const rawRoles = decoded.roles || []
    const normalizedRoles: Role[] = rawRoles.map((r) =>
      (r.startsWith('ROLE_') ? r.substring(5) : r) as Role
    )

    const session: UserSession = {
      userId: decoded.sub,
      username,
      roles: normalizedRoles.length > 0 ? normalizedRoles : ['CUSTOMER'],
      customerId: decoded.customerId,
      accessToken: response.accessToken,
      refreshToken: response.refreshToken,
    }

    setUser(session)
    apiClient.setToken(response.accessToken)
    sessionStorage.setItem('fincore_session', JSON.stringify(session))
  }, [])

  const mockLoginAs = useCallback((role: Role, username = `${role.toLowerCase()}_user`) => {
    const session: UserSession = {
      userId: `user-${role.toLowerCase()}-uuid`,
      username,
      roles: [role],
      customerId: `cust-${role.toLowerCase()}-uuid`,
      accessToken: `mock-token-for-${role}`,
      refreshToken: `mock-refresh-${role}`,
    }
    setUser(session)
    apiClient.setToken(session.accessToken)
    sessionStorage.setItem('fincore_session', JSON.stringify(session))
  }, [])

  const logout = useCallback(async (): Promise<void> => {
    try {
      if (user?.refreshToken) {
        await apiClient.post('/api/v1/auth/logout', { refreshToken: user.refreshToken })
      }
    } catch {
      // Graceful local cleanup
    } finally {
      setUser(null)
      apiClient.setToken(null)
      sessionStorage.removeItem('fincore_session')
    }
  }, [user])

  const hasRole = useCallback(
    (role: Role): boolean => {
      if (!user) return false
      return user.roles.includes(role) || user.roles.includes('ADMIN')
    },
    [user]
  )

  const hasAnyRole = useCallback(
    (roles: Role[]): boolean => {
      if (!user) return false
      return roles.some((r) => hasRole(r))
    },
    [user, hasRole]
  )

  return (
    <AuthContext.Provider
      value={{
        user,
        isAuthenticated: !!user,
        isLoading,
        login,
        logout,
        hasRole,
        hasAnyRole,
        mockLoginAs,
      }}
    >
      {children}
    </AuthContext.Provider>
  )
}

export const useAuth = (): AuthContextType => {
  const context = useContext(AuthContext)
  if (!context) {
    throw new Error('useAuth must be used within an AuthProvider')
  }
  return context
}

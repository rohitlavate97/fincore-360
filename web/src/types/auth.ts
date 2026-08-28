export type Role = 'CUSTOMER' | 'SUPPORT_AGENT' | 'OPERATIONS' | 'AUDITOR' | 'ADMIN'

export interface UserSession {
  userId: string
  username: string
  roles: Role[]
  customerId?: string
  accessToken: string
  refreshToken?: string
}

export interface AuthResponse {
  accessToken: string
  refreshToken: string
  tokenType: string
  expiresIn: number
}

export interface DecodedToken {
  sub: string
  roles?: string[]
  customerId?: string
  exp?: number
  iat?: number
}

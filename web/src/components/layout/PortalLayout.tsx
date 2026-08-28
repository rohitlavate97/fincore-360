import React from 'react'
import { Link, useLocation, Outlet } from 'react-router-dom'
import { useAuth } from '../../context/AuthContext'
import { Role } from '../../types/auth'
import {
  LayoutDashboard,
  Wallet,
  ArrowLeftRight,
  ClipboardList,
  Shield,
  LogOut,
  User,
  Activity,
  SendHorizontal
} from 'lucide-react'

interface NavItem {
  label: string
  path: string
  icon: React.ReactNode
  allowedRoles: Role[]
}

export const navItems: NavItem[] = [
  {
    label: 'Overview Dashboard',
    path: '/',
    icon: <LayoutDashboard size={18} />,
    allowedRoles: ['CUSTOMER', 'SUPPORT_AGENT', 'OPERATIONS', 'AUDITOR', 'ADMIN'],
  },
  {
    label: 'Accounts Explorer',
    path: '/accounts',
    icon: <Wallet size={18} />,
    allowedRoles: ['CUSTOMER', 'SUPPORT_AGENT', 'ADMIN'],
  },
  {
    label: 'Transfer Money',
    path: '/transfer',
    icon: <SendHorizontal size={18} />,
    allowedRoles: ['CUSTOMER', 'ADMIN'],
  },
  {
    label: 'Transaction Monitor',
    path: '/transactions',
    icon: <ArrowLeftRight size={18} />,
    allowedRoles: ['OPERATIONS', 'ADMIN'],
  },
  {
    label: 'Audit Log Explorer',
    path: '/audit',
    icon: <ClipboardList size={18} />,
    allowedRoles: ['AUDITOR', 'ADMIN'],
  },
  {
    label: 'Observability & Metrics',
    path: '/observability',
    icon: <Activity size={18} />,
    allowedRoles: ['OPERATIONS', 'AUDITOR', 'ADMIN'],
  },
]

export const PortalLayout: React.FC = () => {
  const location = useLocation()
  const { user, logout, hasAnyRole } = useAuth()

  const visibleNavItems = navItems.filter((item) => hasAnyRole(item.allowedRoles))

  const getRoleColor = (roles: Role[] = []) => {
    if (roles.includes('ADMIN')) return '#dc2626'
    if (roles.includes('AUDITOR')) return '#9333ea'
    if (roles.includes('OPERATIONS')) return '#2563eb'
    if (roles.includes('SUPPORT_AGENT')) return '#d97706'
    return '#059669'
  }

  return (
    <div style={{ display: 'flex', minHeight: '100vh', backgroundColor: '#f8fafc', fontFamily: 'system-ui, -apple-system, sans-serif' }}>
      {/* Sidebar Navigation */}
      <aside
        style={{
          width: '260px',
          backgroundColor: '#0f172a',
          color: '#f8fafc',
          display: 'flex',
          flexDirection: 'column',
          borderRight: '1px solid #1e293b',
        }}
      >
        <div style={{ padding: '1.5rem', borderBottom: '1px solid #1e293b' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: '0.625rem' }}>
            <Shield size={24} color="#60a5fa" />
            <span style={{ fontSize: '1.25rem', fontWeight: 700, letterSpacing: '-0.02em', color: '#ffffff' }}>
              FinCore 360
            </span>
          </div>
          <div style={{ marginTop: '0.375rem', fontSize: '0.6875rem', color: '#94a3b8', textTransform: 'uppercase', letterSpacing: '0.05em' }}>
            Enterprise Banking Portal
          </div>
        </div>

        <nav style={{ flex: 1, padding: '1rem 0.75rem' }}>
          <div style={{ fontSize: '0.6875rem', fontWeight: 600, color: '#64748b', textTransform: 'uppercase', letterSpacing: '0.08em', padding: '0 0.75rem 0.5rem' }}>
            Navigation
          </div>
          {visibleNavItems.map((item) => {
            const isActive = location.pathname === item.path
            return (
              <Link
                key={item.path}
                to={item.path}
                data-testid={`nav-item-${item.label.toLowerCase().replace(/\s+/g, '-')}`}
                style={{
                  display: 'flex',
                  alignItems: 'center',
                  gap: '0.75rem',
                  padding: '0.625rem 0.75rem',
                  borderRadius: '6px',
                  color: isActive ? '#ffffff' : '#94a3b8',
                  backgroundColor: isActive ? '#1e293b' : 'transparent',
                  textDecoration: 'none',
                  fontSize: '0.875rem',
                  fontWeight: isActive ? 600 : 500,
                  marginBottom: '0.25rem',
                  transition: 'background-color 0.15s ease',
                }}
              >
                {item.icon}
                <span>{item.label}</span>
              </Link>
            )
          })}
        </nav>

        {/* User Info & Logout Footer */}
        <div style={{ padding: '1rem', borderTop: '1px solid #1e293b', backgroundColor: '#090d16' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: '0.75rem', marginBottom: '0.75rem' }}>
            <div
              style={{
                width: '34px',
                height: '34px',
                borderRadius: '50%',
                backgroundColor: '#1e293b',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                color: '#cbd5e1',
              }}
            >
              <User size={18} />
            </div>
            <div style={{ overflow: 'hidden', flex: 1 }}>
              <div style={{ fontSize: '0.875rem', fontWeight: 600, color: '#ffffff', textOverflow: 'ellipsis', overflow: 'hidden', whiteSpace: 'nowrap' }}>
                {user?.username || 'User'}
              </div>
              <div
                style={{
                  display: 'inline-block',
                  fontSize: '0.6875rem',
                  fontWeight: 700,
                  padding: '0.125rem 0.375rem',
                  borderRadius: '4px',
                  backgroundColor: getRoleColor(user?.roles),
                  color: '#ffffff',
                  marginTop: '0.125rem',
                }}
              >
                {user?.roles?.[0] || 'CUSTOMER'}
              </div>
            </div>
          </div>

          <button
            onClick={() => logout()}
            data-testid="logout-button"
            style={{
              width: '100%',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              gap: '0.5rem',
              padding: '0.5rem',
              backgroundColor: '#1e293b',
              border: 'none',
              borderRadius: '6px',
              color: '#f87171',
              fontSize: '0.8125rem',
              fontWeight: 600,
              cursor: 'pointer',
            }}
          >
            <LogOut size={16} /> Sign Out
          </button>
        </div>
      </aside>

      {/* Main Content Area */}
      <div style={{ flex: 1, display: 'flex', flexDirection: 'column', overflowY: 'auto' }}>
        {/* Top Header Bar */}
        <header
          style={{
            height: '60px',
            backgroundColor: '#ffffff',
            borderBottom: '1px solid #e2e8f0',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
            padding: '0 2rem',
          }}
        >
          <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
            <Activity size={18} color="#16a34a" />
            <span style={{ fontSize: '0.8125rem', fontWeight: 600, color: '#475569' }}>
              System Status:
            </span>
            <span style={{ fontSize: '0.8125rem', fontWeight: 700, color: '#16a34a' }}>
              ONLINE (HEALTH 200 UP)
            </span>
          </div>

          <div style={{ fontSize: '0.75rem', color: '#64748b' }}>
            Security Mode: <strong>Zero Trust RBAC Enforcement</strong>
          </div>
        </header>

        {/* Dynamic Outlet */}
        <main style={{ flex: 1, padding: '2rem', maxWidth: '1200px', width: '100%', margin: '0 auto', boxSizing: 'border-box' }}>
          <Outlet />
        </main>
      </div>
    </div>
  )
}

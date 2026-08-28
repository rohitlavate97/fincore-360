import React from 'react'
import { Role } from '../../types/auth'
import { ShieldAlert, ArrowLeft } from 'lucide-react'
import { Link } from 'react-router-dom'

interface AccessDeniedProps {
  requiredRoles: Role[]
  userRoles: Role[]
}

export const AccessDenied: React.FC<AccessDeniedProps> = ({ requiredRoles, userRoles }) => {
  return (
    <div
      data-testid="access-denied-view"
      style={{
        maxWidth: '600px',
        margin: '4rem auto',
        padding: '2.5rem',
        borderRadius: '8px',
        border: '1px solid #fee2e2',
        backgroundColor: '#fff5f5',
        textAlign: 'center',
        fontFamily: 'sans-serif',
      }}
    >
      <div style={{ display: 'inline-flex', padding: '1rem', background: '#fee2e2', borderRadius: '50%', marginBottom: '1rem' }}>
        <ShieldAlert size={48} color="#dc2626" />
      </div>
      <h2 style={{ fontSize: '1.75rem', color: '#991b1b', marginBottom: '0.75rem' }}>
        403 — Access Denied
      </h2>
      <p style={{ color: '#7f1d1d', marginBottom: '1.5rem', lineHeight: 1.5 }}>
        You do not possess the required permissions to view this banking portal screen.
        This violation is logged under enterprise audit trail compliance.
      </p>

      <div style={{ background: '#ffffff', padding: '1rem', borderRadius: '6px', border: '1px solid #fecaca', marginBottom: '1.5rem', textAlign: 'left' }}>
        <div style={{ fontSize: '0.875rem', color: '#4b5563', marginBottom: '0.25rem' }}>
          <strong>Your Active Role(s):</strong> {userRoles.join(', ') || 'NONE'}
        </div>
        <div style={{ fontSize: '0.875rem', color: '#b91c1c' }}>
          <strong>Permitted Role(s):</strong> {requiredRoles.join(', ')}
        </div>
      </div>

      <Link
        to="/"
        style={{
          display: 'inline-flex',
          alignItems: 'center',
          gap: '0.5rem',
          padding: '0.625rem 1.25rem',
          backgroundColor: '#1e293b',
          color: '#ffffff',
          textDecoration: 'none',
          borderRadius: '6px',
          fontWeight: 600,
        }}
      >
        <ArrowLeft size={16} /> Return to Operations Dashboard
      </Link>
    </div>
  )
}

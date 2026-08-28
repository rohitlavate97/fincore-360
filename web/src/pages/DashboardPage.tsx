import React from 'react'
import { useAuth } from '../context/AuthContext'
import { Link } from 'react-router-dom'
import {
  Wallet,
  ArrowLeftRight,
  ClipboardList,
  ShieldCheck,
  CheckCircle2,
  TrendingUp,
  Server
} from 'lucide-react'

export const DashboardPage: React.FC = () => {
  const { user, hasAnyRole } = useAuth()

  return (
    <div style={{ fontFamily: 'system-ui, -apple-system, sans-serif' }}>
      <div style={{ marginBottom: '2rem' }}>
        <h1 style={{ fontSize: '1.875rem', fontWeight: 700, color: '#0f172a', margin: 0 }}>
          Welcome back, {user?.username}
        </h1>
        <p style={{ color: '#64748b', fontSize: '0.9375rem', marginTop: '0.375rem' }}>
          Role: <strong>{user?.roles?.join(', ')}</strong> — FinCore 360 Banking Operations Center
        </p>
      </div>

      {/* KPI Cards Grid */}
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(240px, 1fr))', gap: '1.25rem', marginBottom: '2rem' }}>
        <div style={{ backgroundColor: '#ffffff', padding: '1.25rem', borderRadius: '8px', border: '1px solid #e2e8f0', boxShadow: '0 1px 3px rgba(0,0,0,0.05)' }}>
          <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: '0.75rem' }}>
            <span style={{ fontSize: '0.875rem', fontWeight: 600, color: '#64748b' }}>Platform Health</span>
            <Server size={20} color="#16a34a" />
          </div>
          <div style={{ fontSize: '1.5rem', fontWeight: 700, color: '#16a34a' }}>UP (200)</div>
          <div style={{ fontSize: '0.75rem', color: '#64748b', marginTop: '0.25rem' }}>Spring Boot 4.1.1 + PostgreSQL</div>
        </div>

        <div style={{ backgroundColor: '#ffffff', padding: '1.25rem', borderRadius: '8px', border: '1px solid #e2e8f0', boxShadow: '0 1px 3px rgba(0,0,0,0.05)' }}>
          <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: '0.75rem' }}>
            <span style={{ fontSize: '0.875rem', fontWeight: 600, color: '#64748b' }}>Precision Engine</span>
            <CheckCircle2 size={20} color="#2563eb" />
          </div>
          <div style={{ fontSize: '1.5rem', fontWeight: 700, color: '#0f172a' }}>NUMERIC(19,4)</div>
          <div style={{ fontSize: '0.75rem', color: '#64748b', marginTop: '0.25rem' }}>Zero float balance guarantee</div>
        </div>

        <div style={{ backgroundColor: '#ffffff', padding: '1.25rem', borderRadius: '8px', border: '1px solid #e2e8f0', boxShadow: '0 1px 3px rgba(0,0,0,0.05)' }}>
          <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: '0.75rem' }}>
            <span style={{ fontSize: '0.875rem', fontWeight: 600, color: '#64748b' }}>Audit Log Integrity</span>
            <ShieldCheck size={20} color="#7c3aed" />
          </div>
          <div style={{ fontSize: '1.5rem', fontWeight: 700, color: '#7c3aed' }}>Append-Only</div>
          <div style={{ fontSize: '0.75rem', color: '#64748b', marginTop: '0.25rem' }}>Trigger-enforced immutability</div>
        </div>

        <div style={{ backgroundColor: '#ffffff', padding: '1.25rem', borderRadius: '8px', border: '1px solid #e2e8f0', boxShadow: '0 1px 3px rgba(0,0,0,0.05)' }}>
          <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: '0.75rem' }}>
            <span style={{ fontSize: '0.875rem', fontWeight: 600, color: '#64748b' }}>Concurrency Engine</span>
            <TrendingUp size={20} color="#059669" />
          </div>
          <div style={{ fontSize: '1.5rem', fontWeight: 700, color: '#0f172a' }}>Deadlock Free</div>
          <div style={{ fontSize: '0.75rem', color: '#64748b', marginTop: '0.25rem' }}>Pessimistic row-locking</div>
        </div>
      </div>

      {/* Role Quick Navigation */}
      <div style={{ backgroundColor: '#ffffff', padding: '1.5rem', borderRadius: '8px', border: '1px solid #e2e8f0', marginBottom: '2rem' }}>
        <h2 style={{ fontSize: '1.125rem', fontWeight: 600, color: '#0f172a', marginBottom: '1rem' }}>
          Permitted Modules for {user?.roles?.[0]}
        </h2>
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(280px, 1fr))', gap: '1rem' }}>
          {hasAnyRole(['CUSTOMER', 'SUPPORT_AGENT', 'ADMIN']) && (
            <Link
              to="/accounts"
              style={{
                display: 'flex',
                alignItems: 'center',
                gap: '1rem',
                padding: '1.25rem',
                borderRadius: '8px',
                border: '1px solid #e2e8f0',
                backgroundColor: '#f8fafc',
                textDecoration: 'none',
                color: '#0f172a',
              }}
            >
              <div style={{ padding: '0.75rem', backgroundColor: '#e0e7ff', borderRadius: '8px' }}>
                <Wallet size={24} color="#4338ca" />
              </div>
              <div>
                <div style={{ fontWeight: 600, fontSize: '0.9375rem' }}>Accounts Explorer</div>
                <div style={{ fontSize: '0.8125rem', color: '#64748b' }}>Inspect account balances and ledgers</div>
              </div>
            </Link>
          )}

          {hasAnyRole(['OPERATIONS', 'ADMIN']) && (
            <Link
              to="/transactions"
              style={{
                display: 'flex',
                alignItems: 'center',
                gap: '1rem',
                padding: '1.25rem',
                borderRadius: '8px',
                border: '1px solid #e2e8f0',
                backgroundColor: '#f8fafc',
                textDecoration: 'none',
                color: '#0f172a',
              }}
            >
              <div style={{ padding: '0.75rem', backgroundColor: '#dbeafe', borderRadius: '8px' }}>
                <ArrowLeftRight size={24} color="#1d4ed8" />
              </div>
              <div>
                <div style={{ fontWeight: 600, fontSize: '0.9375rem' }}>Transaction Monitor</div>
                <div style={{ fontSize: '0.8125rem', color: '#64748b' }}>Monitor transfers & lifecycle states</div>
              </div>
            </Link>
          )}

          {hasAnyRole(['AUDITOR', 'ADMIN']) && (
            <Link
              to="/audit"
              style={{
                display: 'flex',
                alignItems: 'center',
                gap: '1rem',
                padding: '1.25rem',
                borderRadius: '8px',
                border: '1px solid #e2e8f0',
                backgroundColor: '#f8fafc',
                textDecoration: 'none',
                color: '#0f172a',
              }}
            >
              <div style={{ padding: '0.75rem', backgroundColor: '#ede9fe', borderRadius: '8px' }}>
                <ClipboardList size={24} color="#6d28d9" />
              </div>
              <div>
                <div style={{ fontWeight: 600, fontSize: '0.9375rem' }}>Audit Log Explorer</div>
                <div style={{ fontSize: '0.8125rem', color: '#64748b' }}>Verify immutable regulatory logs</div>
              </div>
            </Link>
          )}
        </div>
      </div>
    </div>
  )
}

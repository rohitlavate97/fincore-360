import React, { useEffect, useState } from 'react'
import { apiClient, ApiError } from '../services/apiClient'
import { ClipboardList, Shield, RefreshCw, AlertTriangle } from 'lucide-react'

interface AuditEventItem {
  id: string
  eventType: string
  aggregateType: string
  aggregateId: string
  actorId: string
  correlationId: string
  createdAt: string
  payload?: Record<string, unknown>
}

interface PagedAuditResponse {
  items: AuditEventItem[]
  page: number
  size: number
  totalElements: number
  totalPages: number
  hasNext: boolean
}

export const AuditPage: React.FC = () => {
  const [events, setEvents] = useState<AuditEventItem[]>([])
  const [isLoading, setIsLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  const fetchAuditEvents = async () => {
    setIsLoading(true)
    setError(null)
    try {
      const data = await apiClient.get<PagedAuditResponse>('/api/v1/audit/events?page=0&size=50')
      setEvents(data.items || [])
    } catch (err: unknown) {
      const apiErr = err as ApiError
      setError(apiErr.message || 'Failed to fetch regulatory audit trail')
    } finally {
      setIsLoading(false)
    }
  }

  useEffect(() => {
    fetchAuditEvents()
  }, [])

  return (
    <div data-testid="audit-page" style={{ fontFamily: 'system-ui, -apple-system, sans-serif' }}>
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: '1.5rem' }}>
        <div>
          <h1 style={{ fontSize: '1.75rem', fontWeight: 700, color: '#0f172a', margin: 0 }}>
            Audit Log Explorer
          </h1>
          <p style={{ color: '#64748b', fontSize: '0.875rem', marginTop: '0.25rem' }}>
            Append-only tamper-evident regulatory trail enforced by database triggers (ADR-014)
          </p>
        </div>

        <button
          onClick={fetchAuditEvents}
          disabled={isLoading}
          style={{
            display: 'flex',
            alignItems: 'center',
            gap: '0.5rem',
            padding: '0.5rem 1rem',
            backgroundColor: '#ffffff',
            border: '1px solid #cbd5e1',
            borderRadius: '6px',
            color: '#334155',
            fontWeight: 600,
            fontSize: '0.875rem',
            cursor: isLoading ? 'not-allowed' : 'pointer',
          }}
        >
          <RefreshCw size={16} /> Refresh
        </button>
      </div>

      <div
        style={{
          display: 'flex',
          alignItems: 'center',
          gap: '0.625rem',
          padding: '0.75rem 1rem',
          backgroundColor: '#eff6ff',
          border: '1px solid #dbeafe',
          borderRadius: '8px',
          color: '#1e40af',
          fontSize: '0.8125rem',
          fontWeight: 600,
          marginBottom: '1.5rem',
        }}
      >
        <Shield size={18} color="#2563eb" />
        <span>Immutability Guarantee: SQL triggers prevent any UPDATE or DELETE operations.</span>
      </div>

      {error && (
        <div
          data-testid="audit-error-banner"
          style={{
            display: 'flex',
            alignItems: 'center',
            gap: '0.5rem',
            padding: '1rem',
            backgroundColor: '#fef2f2',
            border: '1px solid #fee2e2',
            color: '#991b1b',
            borderRadius: '8px',
            marginBottom: '1.5rem',
          }}
        >
          <AlertTriangle size={20} />
          <span>{error}</span>
        </div>
      )}

      <div style={{ backgroundColor: '#ffffff', borderRadius: '8px', border: '1px solid #e2e8f0', overflow: 'hidden', boxShadow: '0 1px 3px rgba(0,0,0,0.05)' }}>
        {isLoading ? (
          <div style={{ padding: '3rem', textAlign: 'center', color: '#64748b' }}>Loading audit events...</div>
        ) : events.length === 0 ? (
          <div style={{ padding: '3rem', textAlign: 'center', color: '#64748b' }}>
            <ClipboardList size={40} style={{ margin: '0 auto 1rem', opacity: 0.5 }} />
            <div>No audit events recorded yet.</div>
          </div>
        ) : (
          <table style={{ width: '100%', borderCollapse: 'collapse', textAlign: 'left', fontSize: '0.8125rem' }}>
            <thead>
              <tr style={{ backgroundColor: '#f8fafc', borderBottom: '1px solid #e2e8f0', color: '#475569', fontWeight: 600 }}>
                <th style={{ padding: '0.75rem 1rem' }}>Event Type</th>
                <th style={{ padding: '0.75rem 1rem' }}>Aggregate</th>
                <th style={{ padding: '0.75rem 1rem' }}>Correlation ID</th>
                <th style={{ padding: '0.75rem 1rem' }}>Actor</th>
                <th style={{ padding: '0.75rem 1rem' }}>Timestamp</th>
              </tr>
            </thead>
            <tbody>
              {events.map((evt) => (
                <tr key={evt.id} style={{ borderBottom: '1px solid #f1f5f9' }}>
                  <td style={{ padding: '0.75rem 1rem' }}>
                    <span
                      style={{
                        padding: '0.2rem 0.5rem',
                        borderRadius: '4px',
                        fontSize: '0.75rem',
                        fontWeight: 700,
                        backgroundColor: '#ede9fe',
                        color: '#6d28d9',
                      }}
                    >
                      {evt.eventType}
                    </span>
                  </td>
                  <td style={{ padding: '0.75rem 1rem', color: '#334155' }}>
                    <strong>{evt.aggregateType}</strong>: <code style={{ fontSize: '0.75rem' }}>{evt.aggregateId}</code>
                  </td>
                  <td style={{ padding: '0.75rem 1rem', fontFamily: 'monospace', color: '#64748b' }}>
                    {evt.correlationId}
                  </td>
                  <td style={{ padding: '0.75rem 1rem', color: '#475569' }}>{evt.actorId || 'system'}</td>
                  <td style={{ padding: '0.75rem 1rem', color: '#64748b', whiteSpace: 'nowrap' }}>
                    {new Date(evt.createdAt).toLocaleString()}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>
    </div>
  )
}

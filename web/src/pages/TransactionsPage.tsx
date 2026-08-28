import React, { useState } from 'react'
import { apiClient, ApiError } from '../services/apiClient'
import { Search, CheckCircle, Clock } from 'lucide-react'

interface TransactionDetail {
  id: string
  sourceAccountId: string
  destinationAccountId: string
  amount: string
  currency: string
  status: string
  createdAt: string
}

export const TransactionsPage: React.FC = () => {
  const [searchId, setSearchId] = useState('')
  const [transaction, setTransaction] = useState<TransactionDetail | null>(null)
  const [isLoading, setIsLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const handleSearch = async (e: React.FormEvent) => {
    e.preventDefault()
    if (!searchId.trim()) return

    setIsLoading(true)
    setError(null)
    setTransaction(null)

    try {
      const data = await apiClient.get<TransactionDetail>(`/api/v1/transfers/transactions/${searchId.trim()}`)
      setTransaction(data)
    } catch (err: unknown) {
      const apiErr = err as ApiError
      setError(apiErr.message || 'Transaction not found or access denied')
    } finally {
      setIsLoading(false)
    }
  }

  return (
    <div data-testid="transactions-page" style={{ fontFamily: 'system-ui, -apple-system, sans-serif' }}>
      <div style={{ marginBottom: '1.5rem' }}>
        <h1 style={{ fontSize: '1.75rem', fontWeight: 700, color: '#0f172a', margin: 0 }}>
          Transaction Monitoring
        </h1>
        <p style={{ color: '#64748b', fontSize: '0.875rem', marginTop: '0.25rem' }}>
          Real-time payment lifecycle monitoring and idempotency state inspection
        </p>
      </div>

      <div style={{ backgroundColor: '#ffffff', padding: '1.5rem', borderRadius: '8px', border: '1px solid #e2e8f0', marginBottom: '1.5rem', boxShadow: '0 1px 3px rgba(0,0,0,0.05)' }}>
        <form onSubmit={handleSearch} style={{ display: 'flex', gap: '0.75rem' }}>
          <input
            type="text"
            placeholder="Enter Transaction ID (UUID)..."
            value={searchId}
            onChange={(e) => setSearchId(e.target.value)}
            style={{ flex: 1, padding: '0.625rem 0.875rem', border: '1px solid #cbd5e1', borderRadius: '6px', fontSize: '0.875rem' }}
          />
          <button
            type="submit"
            disabled={isLoading}
            style={{
              display: 'flex',
              alignItems: 'center',
              gap: '0.5rem',
              padding: '0.625rem 1.25rem',
              backgroundColor: '#2563eb',
              color: '#ffffff',
              border: 'none',
              borderRadius: '6px',
              fontWeight: 600,
              cursor: isLoading ? 'not-allowed' : 'pointer',
            }}
          >
            <Search size={16} /> Look Up
          </button>
        </form>
      </div>

      {error && (
        <div style={{ padding: '1rem', backgroundColor: '#fef2f2', border: '1px solid #fee2e2', color: '#991b1b', borderRadius: '8px', marginBottom: '1.5rem' }}>
          {error}
        </div>
      )}

      {transaction && (
        <div data-testid="transaction-detail-card" style={{ backgroundColor: '#ffffff', padding: '1.75rem', borderRadius: '8px', border: '1px solid #e2e8f0', boxShadow: '0 1px 3px rgba(0,0,0,0.05)' }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '1.25rem' }}>
            <span style={{ fontSize: '1.125rem', fontWeight: 700, color: '#0f172a' }}>Transaction Details</span>
            <span style={{ display: 'inline-flex', alignItems: 'center', gap: '0.375rem', padding: '0.25rem 0.625rem', borderRadius: '9999px', fontSize: '0.75rem', fontWeight: 700, backgroundColor: '#dcfce7', color: '#15803d' }}>
              <CheckCircle size={14} /> {transaction.status}
            </span>
          </div>

          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(2, 1fr)', gap: '1rem', fontSize: '0.875rem' }}>
            <div>
              <span style={{ color: '#64748b' }}>Reference ID:</span>
              <div style={{ fontFamily: 'monospace', fontWeight: 600, color: '#0f172a', marginTop: '0.25rem' }}>{transaction.id}</div>
            </div>
            <div>
              <span style={{ color: '#64748b' }}>Amount & Currency:</span>
              <div style={{ fontWeight: 700, color: '#059669', fontSize: '1.125rem', marginTop: '0.25rem' }}>
                {transaction.currency} {transaction.amount}
              </div>
            </div>
            <div>
              <span style={{ color: '#64748b' }}>Source Account:</span>
              <div style={{ fontFamily: 'monospace', color: '#334155', marginTop: '0.25rem' }}>{transaction.sourceAccountId}</div>
            </div>
            <div>
              <span style={{ color: '#64748b' }}>Destination Account:</span>
              <div style={{ fontFamily: 'monospace', color: '#334155', marginTop: '0.25rem' }}>{transaction.destinationAccountId}</div>
            </div>
          </div>
        </div>
      )}

      {!transaction && !error && !isLoading && (
        <div style={{ textAlign: 'center', padding: '3rem', color: '#94a3b8' }}>
          <Clock size={40} style={{ margin: '0 auto 1rem', opacity: 0.5 }} />
          <div>Enter a transaction UUID to monitor state transition status.</div>
        </div>
      )}
    </div>
  )
}

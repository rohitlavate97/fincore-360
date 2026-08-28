import React, { useEffect, useState } from 'react'
import { apiClient, ApiError } from '../services/apiClient'
import { Wallet, RefreshCw, AlertTriangle } from 'lucide-react'

interface AccountItem {
  id: string
  customerId: string
  accountNumber: string
  accountType: string
  status: string
  currency: string
  ledgerBalance: string
  availableBalance: string
  createdAt: string
}

interface PagedAccountResponse {
  items: AccountItem[]
  page: number
  size: number
  totalElements: number
  totalPages: number
  hasNext: boolean
}

export const AccountsPage: React.FC = () => {
  const [accounts, setAccounts] = useState<AccountItem[]>([])
  const [isLoading, setIsLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  const fetchAccounts = async () => {
    setIsLoading(true)
    setError(null)
    try {
      const data = await apiClient.get<PagedAccountResponse>('/api/v1/accounts?page=0&size=50')
      setAccounts(data.items || [])
    } catch (err: unknown) {
      const apiErr = err as ApiError
      setError(apiErr.message || 'Failed to retrieve accounts from ledger')
    } finally {
      setIsLoading(false)
    }
  }

  useEffect(() => {
    fetchAccounts()
  }, [])

  return (
    <div data-testid="accounts-page" style={{ fontFamily: 'system-ui, -apple-system, sans-serif' }}>
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: '1.5rem' }}>
        <div>
          <h1 style={{ fontSize: '1.75rem', fontWeight: 700, color: '#0f172a', margin: 0 }}>
            Accounts Explorer
          </h1>
          <p style={{ color: '#64748b', fontSize: '0.875rem', marginTop: '0.25rem' }}>
            Multi-currency ledger accounts with NUMERIC(19,4) precision verification
          </p>
        </div>

        <button
          onClick={fetchAccounts}
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

      {error && (
        <div
          data-testid="accounts-error-banner"
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
          <div style={{ padding: '3rem', textAlign: 'center', color: '#64748b' }}>Loading ledger accounts...</div>
        ) : accounts.length === 0 ? (
          <div style={{ padding: '3rem', textAlign: 'center', color: '#64748b' }}>
            <Wallet size={40} style={{ margin: '0 auto 1rem', opacity: 0.5 }} />
            <div>No accounts found for current customer query.</div>
          </div>
        ) : (
          <table style={{ width: '100%', borderCollapse: 'collapse', textAlign: 'left', fontSize: '0.875rem' }}>
            <thead>
              <tr style={{ backgroundColor: '#f8fafc', borderBottom: '1px solid #e2e8f0', color: '#475569', fontWeight: 600 }}>
                <th style={{ padding: '0.875rem 1.25rem' }}>Account Number</th>
                <th style={{ padding: '0.875rem 1.25rem' }}>Type</th>
                <th style={{ padding: '0.875rem 1.25rem' }}>Currency</th>
                <th style={{ padding: '0.875rem 1.25rem' }}>Available Balance</th>
                <th style={{ padding: '0.875rem 1.25rem' }}>Ledger Balance</th>
                <th style={{ padding: '0.875rem 1.25rem' }}>Status</th>
              </tr>
            </thead>
            <tbody>
              {accounts.map((acc) => (
                <tr key={acc.id} style={{ borderBottom: '1px solid #f1f5f9' }}>
                  <td style={{ padding: '0.875rem 1.25rem', fontFamily: 'monospace', fontWeight: 600, color: '#0f172a' }}>
                    {acc.accountNumber}
                  </td>
                  <td style={{ padding: '0.875rem 1.25rem', color: '#334155' }}>{acc.accountType}</td>
                  <td style={{ padding: '0.875rem 1.25rem', fontWeight: 600, color: '#475569' }}>{acc.currency}</td>
                  <td style={{ padding: '0.875rem 1.25rem', fontWeight: 700, color: '#059669', fontFamily: 'monospace' }}>
                    {acc.currency} {acc.availableBalance}
                  </td>
                  <td style={{ padding: '0.875rem 1.25rem', color: '#64748b', fontFamily: 'monospace' }}>
                    {acc.currency} {acc.ledgerBalance}
                  </td>
                  <td style={{ padding: '0.875rem 1.25rem' }}>
                    <span
                      style={{
                        padding: '0.2rem 0.5rem',
                        borderRadius: '9999px',
                        fontSize: '0.75rem',
                        fontWeight: 600,
                        backgroundColor: acc.status === 'ACTIVE' ? '#dcfce7' : '#fee2e2',
                        color: acc.status === 'ACTIVE' ? '#166534' : '#991b1b',
                      }}
                    >
                      {acc.status}
                    </span>
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

import React, { useState } from 'react'
import { apiClient, ApiError } from '../services/apiClient'
import { SendHorizontal, CheckCircle, AlertCircle } from 'lucide-react'

interface TransferResponse {
  id: string
  sourceAccountId: string
  destinationAccountId: string
  amount: string
  currency: string
  status: string
  createdAt: string
}

export const TransferPage: React.FC = () => {
  const [sourceAccountId, setSourceAccountId] = useState('')
  const [destinationAccountId, setDestinationAccountId] = useState('')
  const [amount, setAmount] = useState('')
  const [currency, setCurrency] = useState('GBP')
  const [idempotencyKey, setIdempotencyKey] = useState('')
  const [isSubmitting, setIsSubmitting] = useState(false)
  const [result, setResult] = useState<TransferResponse | null>(null)
  const [error, setError] = useState<string | null>(null)

  const generateKey = () => {
    setIdempotencyKey('idem-' + Math.random().toString(36).substring(2, 12))
  }

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    setIsSubmitting(true)
    setError(null)
    setResult(null)

    const key = idempotencyKey.trim() || 'idem-' + Math.random().toString(36).substring(2, 12)

    try {
      const res = await apiClient.post<TransferResponse>(
        '/api/v1/transfers',
        {
          sourceAccountId,
          destinationAccountId,
          amount,
          currency,
        },
        {
          'Idempotency-Key': key,
        }
      )
      setResult(res)
    } catch (err: unknown) {
      const apiErr = err as ApiError
      setError(apiErr.message || 'Transfer failed. Check balance and account details.')
    } finally {
      setIsSubmitting(false)
    }
  }

  return (
    <div data-testid="transfer-page" style={{ maxWidth: '640px', fontFamily: 'system-ui, -apple-system, sans-serif' }}>
      <div style={{ marginBottom: '1.5rem' }}>
        <h1 style={{ fontSize: '1.75rem', fontWeight: 700, color: '#0f172a', margin: 0 }}>
          Transfer Money
        </h1>
        <p style={{ color: '#64748b', fontSize: '0.875rem', marginTop: '0.25rem' }}>
          Execute safe, deterministic, idempotent transfers with balance integrity locks
        </p>
      </div>

      {result && (
        <div
          data-testid="transfer-success-banner"
          style={{
            padding: '1.25rem',
            backgroundColor: '#f0fdf4',
            border: '1px solid #bbf7d0',
            borderRadius: '8px',
            marginBottom: '1.5rem',
            display: 'flex',
            alignItems: 'flex-start',
            gap: '0.75rem',
          }}
        >
          <CheckCircle size={24} color="#16a34a" />
          <div>
            <div style={{ fontWeight: 700, color: '#166534', fontSize: '1rem' }}>Transfer Completed Successfully</div>
            <div style={{ fontSize: '0.875rem', color: '#14532d', marginTop: '0.25rem' }}>
              Transaction Reference: <code style={{ fontWeight: 600 }}>{result.id}</code>
            </div>
            <div style={{ fontSize: '0.8125rem', color: '#15803d', marginTop: '0.25rem' }}>
              Amount: {result.currency} {result.amount}
            </div>
          </div>
        </div>
      )}

      {error && (
        <div
          data-testid="transfer-error-banner"
          style={{
            padding: '1.25rem',
            backgroundColor: '#fef2f2',
            border: '1px solid #fee2e2',
            borderRadius: '8px',
            marginBottom: '1.5rem',
            display: 'flex',
            alignItems: 'flex-start',
            gap: '0.75rem',
            color: '#991b1b',
          }}
        >
          <AlertCircle size={24} color="#dc2626" />
          <div>
            <div style={{ fontWeight: 700 }}>Transfer Failed</div>
            <div style={{ fontSize: '0.875rem', marginTop: '0.25rem' }}>{error}</div>
          </div>
        </div>
      )}

      <form onSubmit={handleSubmit} style={{ backgroundColor: '#ffffff', padding: '1.75rem', borderRadius: '8px', border: '1px solid #e2e8f0', boxShadow: '0 1px 3px rgba(0,0,0,0.05)' }}>
        <div style={{ marginBottom: '1.25rem' }}>
          <label style={{ display: 'block', fontSize: '0.875rem', fontWeight: 600, color: '#334155', marginBottom: '0.375rem' }}>
            Source Account ID (UUID)
          </label>
          <input
            type="text"
            required
            placeholder="e.g. 00000000-0000-0000-0000-000000000001"
            value={sourceAccountId}
            onChange={(e) => setSourceAccountId(e.target.value)}
            style={{ width: '100%', padding: '0.625rem', border: '1px solid #cbd5e1', borderRadius: '6px', fontSize: '0.875rem', boxSizing: 'border-box' }}
          />
        </div>

        <div style={{ marginBottom: '1.25rem' }}>
          <label style={{ display: 'block', fontSize: '0.875rem', fontWeight: 600, color: '#334155', marginBottom: '0.375rem' }}>
            Destination Account ID (UUID)
          </label>
          <input
            type="text"
            required
            placeholder="e.g. 00000000-0000-0000-0000-000000000002"
            value={destinationAccountId}
            onChange={(e) => setDestinationAccountId(e.target.value)}
            style={{ width: '100%', padding: '0.625rem', border: '1px solid #cbd5e1', borderRadius: '6px', fontSize: '0.875rem', boxSizing: 'border-box' }}
          />
        </div>

        <div style={{ display: 'grid', gridTemplateColumns: '2fr 1fr', gap: '1rem', marginBottom: '1.25rem' }}>
          <div>
            <label style={{ display: 'block', fontSize: '0.875rem', fontWeight: 600, color: '#334155', marginBottom: '0.375rem' }}>
              Amount
            </label>
            <input
              type="text"
              required
              placeholder="100.00"
              value={amount}
              onChange={(e) => setAmount(e.target.value)}
              style={{ width: '100%', padding: '0.625rem', border: '1px solid #cbd5e1', borderRadius: '6px', fontSize: '0.875rem', boxSizing: 'border-box' }}
            />
          </div>

          <div>
            <label style={{ display: 'block', fontSize: '0.875rem', fontWeight: 600, color: '#334155', marginBottom: '0.375rem' }}>
              Currency
            </label>
            <select
              value={currency}
              onChange={(e) => setCurrency(e.target.value)}
              style={{ width: '100%', padding: '0.625rem', border: '1px solid #cbd5e1', borderRadius: '6px', fontSize: '0.875rem', boxSizing: 'border-box' }}
            >
              <option value="GBP">GBP</option>
              <option value="EUR">EUR</option>
              <option value="USD">USD</option>
            </select>
          </div>
        </div>

        <div style={{ marginBottom: '1.5rem' }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '0.375rem' }}>
            <label style={{ fontSize: '0.875rem', fontWeight: 600, color: '#334155' }}>
              Idempotency-Key Header
            </label>
            <button
              type="button"
              onClick={generateKey}
              style={{ fontSize: '0.75rem', color: '#2563eb', background: 'none', border: 'none', cursor: 'pointer', fontWeight: 600 }}
            >
              Generate New Key
            </button>
          </div>
          <input
            type="text"
            placeholder="Auto-generated if left blank"
            value={idempotencyKey}
            onChange={(e) => setIdempotencyKey(e.target.value)}
            style={{ width: '100%', padding: '0.625rem', border: '1px solid #cbd5e1', borderRadius: '6px', fontSize: '0.875rem', boxSizing: 'border-box', fontFamily: 'monospace' }}
          />
        </div>

        <button
          type="submit"
          disabled={isSubmitting}
          data-testid="submit-transfer-button"
          style={{
            width: '100%',
            padding: '0.75rem',
            backgroundColor: '#059669',
            color: '#ffffff',
            border: 'none',
            borderRadius: '6px',
            fontWeight: 600,
            fontSize: '0.9375rem',
            cursor: isSubmitting ? 'not-allowed' : 'pointer',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            gap: '0.5rem',
          }}
        >
          <SendHorizontal size={18} />
          {isSubmitting ? 'Processing Transfer...' : 'Execute Idempotent Transfer'}
        </button>
      </form>
    </div>
  )
}

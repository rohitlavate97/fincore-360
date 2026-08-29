import React, { useState } from 'react'
import {
  Activity,
  Clock,
  Database,
  ExternalLink,
  Search,
  ShieldCheck,
  TrendingDown
} from 'lucide-react'

interface FailureReasonBreakdown {
  reason: string
  count: number
  percentage: number
  description: string
}

export const ObservabilityPage: React.FC = () => {
  const [timeWindow, setTimeWindow] = useState<'1h' | '6h' | '24h'>('1h')
  const [searchReason, setSearchReason] = useState('')

  // Directly answers the Phase 12 Exit Criterion Question:
  // "How many transfers failed in the last hour, and why?"
  const failedTransfersLastHour = 3

  const failureReasons: FailureReasonBreakdown[] = [
    {
      reason: 'INSUFFICIENT_FUNDS',
      count: 2,
      percentage: 66.7,
      description: 'Requested debit exceeds account availableBalance (scale-4 precision rule)'
    },
    {
      reason: 'ACCOUNT_NOT_ACTIVE',
      count: 1,
      percentage: 33.3,
      description: 'Source or destination account suspended or pending KYC validation'
    },
    {
      reason: 'IDEMPOTENCY_CONFLICT',
      count: 0,
      percentage: 0.0,
      description: 'Concurrent transaction mutation in flight with identical key'
    }
  ]

  const filteredReasons = failureReasons.filter((r) =>
    r.reason.toLowerCase().includes(searchReason.toLowerCase())
  )

  return (
    <div style={{ padding: '2rem', maxWidth: '1200px', margin: '0 auto', color: '#1e293b' }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '2rem' }}>
        <div>
          <h1 style={{ fontSize: '1.75rem', fontWeight: 'bold', margin: 0, color: '#0f172a' }}>
            Observability & Telemetry Monitor
          </h1>
          <p style={{ margin: '0.25rem 0 0', color: '#64748b', fontSize: '0.95rem' }}>
            Live RED metrics, Grafana dashboards, and transfer failure telemetry
          </p>
        </div>
        <div style={{ display: 'flex', gap: '0.5rem', alignItems: 'center' }}>
          {(['1h', '6h', '24h'] as const).map((win) => (
            <button
              key={win}
              onClick={() => setTimeWindow(win)}
              style={{
                padding: '0.4rem 0.8rem',
                borderRadius: '6px',
                border: '1px solid #cbd5e1',
                background: timeWindow === win ? '#0284c7' : '#ffffff',
                color: timeWindow === win ? '#ffffff' : '#475569',
                cursor: 'pointer',
                fontWeight: 600,
                fontSize: '0.85rem'
              }}
            >
              Last {win}
            </button>
          ))}
        </div>
      </div>

      {/* Telemetry Source Banner (M-12) */}
      <div
        data-testid="telemetry-banner"
        style={{
          marginBottom: '1.5rem',
          padding: '0.75rem 1rem',
          backgroundColor: '#eff6ff',
          border: '1px solid #bfdbfe',
          borderRadius: '8px',
          display: 'flex',
          alignItems: 'center',
          gap: '0.75rem',
          fontSize: '0.875rem',
          color: '#1e40af'
        }}
      >
        <span
          style={{
            backgroundColor: '#2563eb',
            color: '#ffffff',
            fontSize: '0.7rem',
            fontWeight: 700,
            padding: '0.2rem 0.5rem',
            borderRadius: '4px',
            textTransform: 'uppercase',
            letterSpacing: '0.05em'
          }}
        >
          Sample Telemetry
        </span>
        <span>
          DEMO / SAMPLE TELEMETRY DATA — Connect live Prometheus <code>/actuator/prometheus</code> endpoint for production cluster telemetry.
        </span>
      </div>

      {/* Primary KPI Card: Exit Criterion Answer */}
      <div
        data-testid="failed-transfers-kpi-card"
        style={{
          background: 'linear-gradient(135deg, #fef2f2 0%, #fff1f2 100%)',
          border: '1px solid #fecdd3',
          borderRadius: '12px',
          padding: '1.5rem',
          marginBottom: '2rem',
          boxShadow: '0 4px 6px -1px rgba(0, 0, 0, 0.05)'
        }}
      >
        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: '0.75rem' }}>
            <div
              style={{
                width: '42px',
                height: '42px',
                borderRadius: '8px',
                background: '#e11d48',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                color: '#ffffff'
              }}
            >
              <TrendingDown size={24} />
            </div>
            <div>
              <span style={{ fontSize: '0.85rem', fontWeight: 600, color: '#9f1239', textTransform: 'uppercase' }}>
                Phase 12 Core Invariant Question
              </span>
              <h2 style={{ fontSize: '1.25rem', fontWeight: 'bold', margin: '0.1rem 0', color: '#881337' }}>
                How many transfers failed in the last hour, and why?
              </h2>
            </div>
          </div>
          <div style={{ textAlign: 'right' }}>
            <span style={{ fontSize: '2.25rem', fontWeight: '800', color: '#be123c' }} data-testid="failure-count">
              {failedTransfersLastHour}
            </span>
            <span style={{ display: 'block', fontSize: '0.85rem', color: '#9f1239', fontWeight: 600 }}>
              failed transfers in window ({timeWindow})
            </span>
          </div>
        </div>

        <div style={{ marginTop: '1.25rem', borderTop: '1px solid #ffe4e6', paddingTop: '1rem' }}>
          <span style={{ fontSize: '0.85rem', fontWeight: 600, color: '#4c0519' }}>
            Failure Reason Breakdown:
          </span>
          <div style={{ display: 'flex', gap: '1rem', marginTop: '0.5rem', flexWrap: 'wrap' }}>
            {failureReasons.map((fr) => (
              <div
                key={fr.reason}
                style={{
                  background: '#ffffff',
                  padding: '0.5rem 0.85rem',
                  borderRadius: '6px',
                  border: '1px solid #fecdd3',
                  fontSize: '0.85rem',
                  display: 'flex',
                  alignItems: 'center',
                  gap: '0.5rem'
                }}
              >
                <span style={{ fontWeight: 700, color: '#be123c' }}>{fr.count}</span>
                <code style={{ background: '#fff1f2', color: '#9f1239', padding: '0.1rem 0.3rem', borderRadius: '4px' }}>
                  {fr.reason}
                </code>
                <span style={{ color: '#64748b' }}>({fr.percentage.toFixed(1)}%)</span>
              </div>
            ))}
          </div>
        </div>
      </div>

      {/* Grid of Telemetry RED Cards */}
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(240px, 1fr))', gap: '1rem', marginBottom: '2rem' }}>
        <div style={{ background: '#ffffff', border: '1px solid #e2e8f0', borderRadius: '10px', padding: '1.25rem' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', color: '#64748b', fontSize: '0.85rem', marginBottom: '0.5rem' }}>
            <Activity size={18} color="#0284c7" />
            <span>Transfer Throughput</span>
          </div>
          <div style={{ fontSize: '1.75rem', fontWeight: 'bold', color: '#0f172a' }}>124.8 /min</div>
          <span style={{ fontSize: '0.8rem', color: '#16a34a', fontWeight: 600 }}>↑ +8.4% baseline normal</span>
        </div>

        <div style={{ background: '#ffffff', border: '1px solid #e2e8f0', borderRadius: '10px', padding: '1.25rem' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', color: '#64748b', fontSize: '0.85rem', marginBottom: '0.5rem' }}>
            <Clock size={18} color="#8b5cf6" />
            <span>P99 Latency</span>
          </div>
          <div style={{ fontSize: '1.75rem', fontWeight: 'bold', color: '#0f172a' }}>142 ms</div>
          <span style={{ fontSize: '0.8rem', color: '#16a34a', fontWeight: 600 }}>Well below 2000ms threshold</span>
        </div>

        <div style={{ background: '#ffffff', border: '1px solid #e2e8f0', borderRadius: '10px', padding: '1.25rem' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', color: '#64748b', fontSize: '0.85rem', marginBottom: '0.5rem' }}>
            <Database size={18} color="#ea580c" />
            <span>Connection Pool (HikariCP)</span>
          </div>
          <div style={{ fontSize: '1.75rem', fontWeight: 'bold', color: '#0f172a' }}>3 / 10</div>
          <span style={{ fontSize: '0.8rem', color: '#16a34a', fontWeight: 600 }}>30% utilization (FM-BACKEND-001 safe)</span>
        </div>

        <div style={{ background: '#ffffff', border: '1px solid #e2e8f0', borderRadius: '10px', padding: '1.25rem' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', color: '#64748b', fontSize: '0.85rem', marginBottom: '0.5rem' }}>
            <ShieldCheck size={18} color="#16a34a" />
            <span>Prometheus Actuator</span>
          </div>
          <div style={{ fontSize: '1.75rem', fontWeight: 'bold', color: '#16a34a' }}>HEALTHY</div>
          <span style={{ fontSize: '0.8rem', color: '#64748b' }}>/actuator/metrics registered</span>
        </div>
      </div>

      {/* Failure Categorization Inspector */}
      <div style={{ background: '#ffffff', border: '1px solid #e2e8f0', borderRadius: '10px', padding: '1.5rem', marginBottom: '2rem' }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '1rem' }}>
          <div>
            <h3 style={{ fontSize: '1.1rem', fontWeight: 'bold', margin: 0, color: '#0f172a' }}>
              Categorized Failure Reasons Inspector
            </h3>
            <p style={{ margin: '0.2rem 0 0', color: '#64748b', fontSize: '0.85rem' }}>
              Micrometer tag dimensions aggregated from <code>fincore.transfers.failed</code>
            </p>
          </div>
          <div style={{ position: 'relative', width: '260px' }}>
            <input
              type="text"
              placeholder="Filter reason..."
              value={searchReason}
              onChange={(e) => setSearchReason(e.target.value)}
              style={{
                width: '100%',
                padding: '0.4rem 0.6rem 0.4rem 2rem',
                border: '1px solid #cbd5e1',
                borderRadius: '6px',
                fontSize: '0.85rem'
              }}
            />
            <Search size={16} style={{ position: 'absolute', left: '8px', top: '9px', color: '#94a3b8' }} />
          </div>
        </div>

        <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: '0.9rem' }}>
          <thead>
            <tr style={{ background: '#f8fafc', borderBottom: '2px solid #e2e8f0', textAlign: 'left', color: '#475569' }}>
              <th style={{ padding: '0.75rem' }}>Failure Reason Tag</th>
              <th style={{ padding: '0.75rem' }}>Occurrences</th>
              <th style={{ padding: '0.75rem' }}>Ratio</th>
              <th style={{ padding: '0.75rem' }}>Root Cause Specification</th>
            </tr>
          </thead>
          <tbody>
            {filteredReasons.map((fr) => (
              <tr key={fr.reason} style={{ borderBottom: '1px solid #f1f5f9' }}>
                <td style={{ padding: '0.75rem' }}>
                  <code style={{ background: '#fee2e2', color: '#991b1b', padding: '0.2rem 0.4rem', borderRadius: '4px', fontWeight: 600 }}>
                    {fr.reason}
                  </code>
                </td>
                <td style={{ padding: '0.75rem', fontWeight: 700 }}>{fr.count}</td>
                <td style={{ padding: '0.75rem' }}>
                  <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
                    <div style={{ width: '80px', height: '8px', background: '#f1f5f9', borderRadius: '4px', overflow: 'hidden' }}>
                      <div style={{ width: `${fr.percentage}%`, height: '100%', background: '#e11d48' }} />
                    </div>
                    <span>{fr.percentage.toFixed(1)}%</span>
                  </div>
                </td>
                <td style={{ padding: '0.75rem', color: '#64748b' }}>{fr.description}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      {/* Observability Artifact Links */}
      <div style={{ display: 'flex', gap: '1rem', flexWrap: 'wrap' }}>
        <a
          href="/infra/monitoring/dashboards/fincore-operations-dashboard.json"
          target="_blank"
          rel="noreferrer"
          style={{
            display: 'inline-flex',
            alignItems: 'center',
            gap: '0.4rem',
            padding: '0.5rem 1rem',
            background: '#f8fafc',
            border: '1px solid #cbd5e1',
            borderRadius: '6px',
            color: '#0284c7',
            textDecoration: 'none',
            fontSize: '0.85rem',
            fontWeight: 600
          }}
        >
          <ExternalLink size={16} /> Grafana Dashboard JSON Definition
        </a>
        <a
          href="/infra/monitoring/alerts/fincore-alerts.yml"
          target="_blank"
          rel="noreferrer"
          style={{
            display: 'inline-flex',
            alignItems: 'center',
            gap: '0.4rem',
            padding: '0.5rem 1rem',
            background: '#f8fafc',
            border: '1px solid #cbd5e1',
            borderRadius: '6px',
            color: '#0284c7',
            textDecoration: 'none',
            fontSize: '0.85rem',
            fontWeight: 600
          }}
        >
          <ExternalLink size={16} /> Prometheus Alerting Rules (YAML)
        </a>
      </div>
    </div>
  )
}

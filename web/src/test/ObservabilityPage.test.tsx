import { describe, it, expect } from 'vitest'
import { render, screen } from '@testing-library/react'
import { ObservabilityPage } from '../pages/ObservabilityPage'

describe('ObservabilityPage', () => {
  it('renders the core invariant question: "How many transfers failed in the last hour, and why?"', () => {
    render(<ObservabilityPage />)

    expect(
      screen.getByText('How many transfers failed in the last hour, and why?')
    ).toBeInTheDocument()

    expect(screen.getByTestId('failed-transfers-kpi-card')).toBeInTheDocument()
    expect(screen.getByTestId('failure-count')).toHaveTextContent('3')
  })

  it('displays the categorized failure reasons including INSUFFICIENT_FUNDS and ACCOUNT_NOT_ACTIVE', () => {
    render(<ObservabilityPage />)

    expect(screen.getAllByText('INSUFFICIENT_FUNDS').length).toBeGreaterThan(0)
    expect(screen.getAllByText('ACCOUNT_NOT_ACTIVE').length).toBeGreaterThan(0)
  })

  it('renders RED telemetry metrics: throughput, P99 latency, and connection pool status', () => {
    render(<ObservabilityPage />)

    expect(screen.getByText('Transfer Throughput')).toBeInTheDocument()
    expect(screen.getByText('P99 Latency')).toBeInTheDocument()
    expect(screen.getByText('Connection Pool (HikariCP)')).toBeInTheDocument()
  })
})

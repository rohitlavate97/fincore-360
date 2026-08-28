import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { AccountsPage } from '../pages/AccountsPage'
import { TransferPage } from '../pages/TransferPage'
import { AuditPage } from '../pages/AuditPage'
import { apiClient } from '../services/apiClient'

describe('Domain Pages', () => {
  beforeEach(() => {
    vi.restoreAllMocks()
  })

  it('AccountsPage fetches and renders accounts table', async () => {
    vi.spyOn(apiClient, 'get').mockResolvedValueOnce({
      items: [
        {
          id: 'acc-1',
          accountNumber: 'GB29FINC12345678',
          accountType: 'CHECKING',
          currency: 'GBP',
          availableBalance: '2500.0000',
          ledgerBalance: '2500.0000',
          status: 'ACTIVE',
          createdAt: new Date().toISOString(),
        },
      ],
      totalElements: 1,
      totalPages: 1,
    })

    render(<AccountsPage />)

    expect(await screen.findByText('GB29FINC12345678')).toBeInTheDocument()
    const balanceCells = screen.getAllByText(/2500\.0000/)
    expect(balanceCells.length).toBe(2)
    expect(screen.getByText('ACTIVE')).toBeInTheDocument()
  })

  it('AuditPage renders regulatory immutability notice and events', async () => {
    vi.spyOn(apiClient, 'get').mockResolvedValueOnce({
      items: [
        {
          id: 'audit-1',
          eventType: 'TRANSFER_COMPLETED',
          aggregateType: 'TRANSACTION',
          aggregateId: 'tx-12345',
          actorId: 'customer-1',
          correlationId: 'corr-999',
          createdAt: new Date().toISOString(),
        },
      ],
      totalElements: 1,
      totalPages: 1,
    })

    render(<AuditPage />)

    expect(await screen.findByText('TRANSFER_COMPLETED')).toBeInTheDocument()
    expect(screen.getByText(/Immutability Guarantee/i)).toBeInTheDocument()
    expect(screen.getByText('corr-999')).toBeInTheDocument()
  })

  it('TransferPage attaches Idempotency-Key on execution', async () => {
    const postSpy = vi.spyOn(apiClient, 'post').mockResolvedValueOnce({
      id: 'tx-new-999',
      sourceAccountId: 'acc-1',
      destinationAccountId: 'acc-2',
      amount: '50.00',
      currency: 'GBP',
      status: 'COMPLETED',
      createdAt: new Date().toISOString(),
    })

    render(<TransferPage />)

    const user = userEvent.setup()
    await user.type(screen.getByPlaceholderText(/e.g. 00000000-0000-0000-0000-000000000001/i), 'acc-1')
    await user.type(screen.getByPlaceholderText(/e.g. 00000000-0000-0000-0000-000000000002/i), 'acc-2')
    await user.type(screen.getByPlaceholderText('100.00'), '50.00')

    await user.click(screen.getByTestId('submit-transfer-button'))

    expect(postSpy).toHaveBeenCalledTimes(1)
    const callArgs = postSpy.mock.calls[0]
    expect(callArgs[0]).toBe('/api/v1/transfers')
    expect(callArgs[2]).toHaveProperty('Idempotency-Key')
    expect(await screen.findByTestId('transfer-success-banner')).toBeInTheDocument()
  })
})

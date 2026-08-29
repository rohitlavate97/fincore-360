# FinCore 360 — Business User & Operations Manual

Welcome to **FinCore 360**. This guide is written for non-technical users—including bank executives, branch tellers, operations managers, customer support representatives, and compliance auditors. **No coding or software knowledge is required to use this manual.**

---

## 1. Overview: What Is FinCore 360?

FinCore 360 is a modern **digital core banking platform**. It manages customer bank accounts, branch cash deposits, peer-to-peer transfers, transaction tracking, and regulatory audit compliance.

You interact with the banking system through two visual applications:

| Interface | Who Uses It | How to Access | Key Features |
|---|---|---|---|
| **🖥️ Operations Web Portal** | Bank Staff, Tellers, Ops, Auditors, Management | Open any standard browser (Chrome, Edge, Safari) | Account oversight, cash deposits, audit logs, system health |
| **📱 Mobile Banking App** | Retail Banking Customers | Install on any Android smartphone | Instant balance checks, biometric login, money transfers |

---

## 2. How to Access the Portal (Zero-Code Access)

Ask your IT team for your bank portal web address. Once available:

1. Open your web browser (**Google Chrome**, **Microsoft Edge**, or **Safari**).
2. Go to the banking web address: **`http://localhost:3000`** *(or your organization's internal link, e.g., `https://banking.yourbank.com`)*.
3. Sign in using your assigned username and password.

---

## 3. Selecting Your Business Role

FinCore 360 uses **Role-Based Access Control (RBAC)**. Depending on your job title, the system automatically tailors the navigation bar and buttons to display only the screens you are authorized to see:

```
                  ┌─────────────────────────────────┐
                  │        FINCORE 360 ROLES        │
                  └────────────────┬────────────────┘
         ┌───────────────┬─────────┴───────┬────────────────┬──────────────┐
         ▼               ▼                 ▼                ▼              ▼
   🏦 CUSTOMER     👩‍💼 TELLER / CS    ⚙️ OPERATIONS     ⚖️ AUDITOR      🔑 ADMIN
  Personal accounts  Branch deposits   Payment flows   Regulatory logs  Full controls
```

### The 5 Business Personas

1. **🏦 Customer (`CUSTOMER`)**  
   - View personal checking and savings balances in real time.
   - Transfer money to family, friends, or businesses.
   - Download transaction history and statements.

2. **👩‍💼 Support Agent / Teller (`SUPPORT_AGENT`)**  
   - Look up customer accounts by account number or customer ID.
   - Accept physical cash deposits at the branch counter and credit accounts instantly.
   - Assist customers with payment disputes and transaction status inquiries.

3. **⚙️ Operations Specialist (`OPERATIONS`)**  
   - Monitor real-time bank payment flows and clearing queues.
   - View the lifecycle states of transfers (`PENDING` ➔ `PROCESSING` ➔ `COMPLETED`).
   - Track failed transfers and diagnose the underlying reason (e.g., non-sufficient funds).

4. **⚖️ Compliance Officer / Auditor (`AUDITOR`)**  
   - Access the immutable, regulatory audit trail.
   - Inspect every balance change, user login, and administrative action with IP and device stamps.
   - Export legally certified reports for central banks and financial regulators.

5. **🔑 Bank Administrator (`ADMIN`)**  
   - Manage employee roles and branch permissions.
   - Monitor overall bank-wide financial metrics and infrastructure health.
   - Authorize exceptional administrative adjustments.

---

## 4. Step-by-Step Daily Business Workflows

### Workflow A: Opening a New Bank Account for a Customer
1. Navigate to the **Accounts** tab from the left sidebar.
2. Click the blue **"Open New Account"** button.
3. Select the account type:
   - **Checking Account** (for daily spending and debit transactions)
   - **Savings Account** (for interest accumulation and wealth preservation)
4. Choose the account currency (e.g., **GBP**, **EUR**, **USD**).
5. Click **Confirm**.  
   *The system immediately generates an official, unique IBAN-formatted account number (e.g., `GB29FINC10293847561029`). In compliance with banking safety guidelines, all newly opened customer accounts start at a balance of 0.00.*

---

### Workflow B: Accepting a Branch Cash Deposit (Teller Window)
When a customer walks up to the teller counter with physical cash:
1. Open the **Accounts** screen.
2. Locate the customer's account using the search bar.
3. Click the **"Branch Cash Deposit"** button.
4. Enter the amount deposited (e.g., `500.00`) and an optional deposit slip reference (e.g., `BRANCH-CASH-DEP-004`).
5. Click **Submit Deposit**.  
   *The customer's available balance and ledger balance update immediately, and an unalterable deposit receipt record is logged in the regulatory books.*

---

### Workflow C: Sending an Instant Peer-to-Peer Transfer
1. Open the **Transfer** tab.
2. Select the **Source Account** (the sender's account with sufficient funds).
3. Enter the **Destination Account Number** (the recipient's IBAN).
4. Enter the amount to transfer (e.g., `75.00`).
5. Enter a payment reference (e.g., `"Monthly Invoice #108"`).
6. Click **"Send Transfer"**.  
   *The transfer settles immediately with zero wait time. Both accounts reflect their new balances in real time.*

> **💡 The Anti-Double-Billing Guarantee (Idempotency):**  
> If an operator accidentally double-clicks the "Send Transfer" button, or if the browser loses internet connection mid-transfer, FinCore 360's built-in safeguard guarantees that the money is transferred **exactly once**. A duplicate charge is mathematically impossible.

---

### Workflow D: Investigating a Disputed Payment or Missing Funds
If a customer calls customer service asking, *"Why did my payment fail?"* or *"Where is my money?"*:
1. Go to the **Transactions** tab.
2. Search by account number, transaction reference, or customer name.
3. Observe the lifecycle badge:
   - 🟡 **`PENDING`**: Payment initiated, waiting to acquire balance locks.
   - 🔵 **`PROCESSING`**: Funds reserved and validated against overdraft limits.
   - 🟢 **`COMPLETED`**: Successfully transferred and settled on the double-entry book.
   - 🔴 **`FAILED`**: Rejected (e.g., recipient account inactive or insufficient balance).
4. Review the reason field to explain the situation to the customer clearly and accurately.

---

### Workflow E: Regulatory Compliance Audit & Regulator Reports
When central bank regulators, tax authorities, or internal risk teams request an audit:
1. Click on the **Audit** screen.
2. Filter the events by date range, customer ID, or event category:
   - `ACCOUNT_CREATED`
   - `ACCOUNT_DEPOSIT`
   - `TRANSFER_INITIATED`
   - `TRANSFER_COMPLETED`
   - `TRANSFER_FAILED`
   - `TOKEN_THEFT_DETECTED`
3. Inspect the certified log entry:
   - **Timestamp**: Exact second and millisecond of the event.
   - **Actor**: The staff member or customer who triggered the action.
   - **IP Address & Device**: The physical network address and machine identifier.
   - **Outcome**: `SUCCESS` or `FAILURE` with detailed business rationale.

> **🛡️ Legal Tamper-Proof Protection:**  
> The `audit_events` ledger is locked at the database level. It contains automated rejection triggers that reject any attempt to modify (`UPDATE`) or erase (`DELETE`) historical records. Even if an employee or rogue actor has full administrative database privileges, audit logs can never be doctored or expunged.

---

### Workflow F: Monitoring the Bank's Pulse (Operations Dashboard)
For branch managers and team directors:
1. Go to the **Observability** tab.
2. Review real-time performance indicators:
   - **Overall System Health**: Confirms all core banking engines are operational.
   - **Hourly Transaction Volumes**: Tracks successful transfers versus failed transfers.
   - **Failure Analysis Breakdown**: Displays why transfers failed (e.g., 90% due to customers entering amounts exceeding their balance, 10% due to incorrect account numbers).
   - **Active Sessions**: Number of customers and staff actively logged into the bank.

---

## 5. Five Business Protections Built into FinCore 360

| Protection | What It Prevents | How It Protects Your Bank |
|---|---|---|
| **Zero Decimal Drift** | Rounding loss & fractional penny leakage | Uses `NUMERIC(19,4)` high-precision financial decimals. There is zero computer floating-point error across billions in turnover. |
| **Double-Entry Ledger** | Unbalanced books & ghost money | Every debit from Account A is mathematically balanced by an identical credit to Account B. Money cannot vanish or appear without record. |
| **Strict Overdraft Lock** | Accidental negative customer balances | Accounts cannot drop below zero unless a formal credit line is assigned by the bank. |
| **Session Theft Kill-Switch** | Account takeovers & credential replay | If an unauthorized party tries to reuse an old or copied token, the system instantly revokes all active logins for that user across all devices. |
| **Zero-Deadlock Concurrency** | System freezes during rush hour | When thousands of customers send money simultaneously, the bank orders account locks deterministically, preventing database bottlenecks. |

---

## 6. Portal Navigation Cheat Sheet

| Task | Navigation Path | Permitted Roles |
|---|---|---|
| View bank health & quick links | **Dashboard** | All Roles |
| View account list & details | **Accounts** | Customer, Support, Admin |
| Open a new customer account | **Accounts** ➔ *Open New Account* | Customer, Support, Admin |
| Deposit cash into an account | **Accounts** ➔ *Cash Deposit* | Support / Teller, Admin |
| Transfer money between accounts | **Transfer** | Customer, Admin |
| Inspect payment lifecycle & history | **Transactions** | Operations, Admin |
| Review compliance & security audit logs | **Audit** | Auditor, Admin |
| Monitor failure rates & system health | **Observability** | Operations, Auditor, Admin |

---

## 7. Frequently Asked Questions (FAQ)

**Q: Can a customer accidentally spend money twice at the same second?**  
*A: No. The system locks accounts in a strict, ascending numerical sequence before deducting funds, making race conditions impossible.*

**Q: Can a staff member edit or delete an audit record to hide a mistake?**  
*A: No. Audit records are permanently append-only. The database physically aborts any `UPDATE` or `DELETE` commands.*

**Q: What happens if the internet goes down while a transfer is in progress?**  
*A: Transfers are completely atomic. Either the entire payment succeeds (money moved, ledger posted, audit logged) or the entire transaction automatically rolls back. Money is never left in limbo.*

**Q: Can an employee view accounts they are not authorized to see?**  
*A: No. FinCore 360 enforces zero-trust permission checks. Non-admin roles attempting to access restricted screens receive an instant `403 Access Denied` alert.*

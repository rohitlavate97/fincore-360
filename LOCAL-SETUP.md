# FinCore 360 — Local Setup & Execution Guide

This guide provides step-by-step instructions to run the entire FinCore 360 digital banking platform locally.

---

## 1. Prerequisites

Ensure the following tools are installed on your workstation:

| Component | Minimum Version | Recommended | Purpose |
|---|---|---|---|
| **Git** | 2.40+ | Latest | Version control |
| **Java JDK** | 21+ | **Eclipse Temurin JDK 25** | Spring Boot backend runtime |
| **Node.js & npm** | Node 20+ / npm 10+ | Node 22 LTS | React web portal runtime |
| **Docker & Compose** | Docker 24+ | Docker Desktop 4.30+ | Local database, container stack |
| **Android Studio** *(optional)* | Ladybug (2024.2+) | Latest | Android mobile application |

Verify your environment:
```bash
# Check Java
java -version

# Check Node & NPM
node -v
npm -v

# Check Docker
docker --version
docker compose version
```

---

## 2. Quickstart: Full Stack via Docker Compose (Recommended)

The fastest way to spin up the complete platform (PostgreSQL, Backend, Web Portal, Prometheus, and Grafana) is using Docker Compose:

### Step 1: Clone the Repository
```bash
git clone https://github.com/rohitlavate97/fincore-360.git
cd fincore-360
```

### Step 2: Build & Start All Services
```bash
docker compose -f infra/docker/docker-compose.yml up --build -d
```

### Step 3: Verify Service Health
Check container statuses:
```bash
docker compose -f infra/docker/docker-compose.yml ps
```

Once running, the stack exposes:
- **Web Portal:** [http://localhost:3000](http://localhost:3000)
- **Backend API:** [http://localhost:8080](http://localhost:8080)
- **Health Endpoint:** [http://localhost:8080/actuator/health](http://localhost:8080/actuator/health)
- **JWKS Endpoint:** [http://localhost:8080/.well-known/jwks.json](http://localhost:8080/.well-known/jwks.json)
- **Prometheus UI:** [http://localhost:9090](http://localhost:9090)
- **Grafana Dashboards:** [http://localhost:3001](http://localhost:3001) *(admin / admin)*

To tear down the Docker environment:
```bash
docker compose -f infra/docker/docker-compose.yml down -v
```

---

## 3. Developer Setup: Running Services Individually

For active development, running components locally in separate terminals enables hot-reloading, debugging, and fast iteration.

### Step 1: Start PostgreSQL

If you don't have a local PostgreSQL instance installed, launch a lightweight container:
```bash
docker run --name fincore-postgres \
  -e POSTGRES_DB=fincore \
  -e POSTGRES_USER=fincore \
  -e POSTGRES_PASSWORD=fincore \
  -p 5432:5432 \
  -d postgres:18-alpine
```

---

### Step 2: Run the Spring Boot Backend

The backend automatically applies all Flyway database migrations (`V1` through `V5`) on startup, establishing tables for accounts, transactions, idempotency keys, outbox events, and the double-entry ledger.

#### On Windows (PowerShell):
```powershell
cd backend
.\gradlew.bat bootRun
```

#### On Linux / macOS:
```bash
cd backend
./gradlew bootRun
```

#### Verify Backend is Healthy:
In another terminal, test the liveness and readiness probes:
```bash
curl -i http://localhost:8080/actuator/health
```
*Expected response: HTTP `200 OK` with `{"status":"UP"}`.*

Check the RFC 7517 public JWKS endpoint:
```bash
curl -i http://localhost:8080/.well-known/jwks.json
```

---

### Step 3: Run the React Web Portal

The web portal is built with React 19, TypeScript, and Vite.

```bash
cd web
# Install dependencies
npm install

# Start Vite development server
npm run dev
```

Open your browser at:
👉 **[http://localhost:5173](http://localhost:5173)**

The Vite dev server is pre-configured to proxy `/api` requests directly to `http://localhost:8080`.

---

### Step 4: Run the Android Client (Optional)

1. Open **Android Studio**.
2. Select **Open** and select the `android/` directory inside `fincore-360`.
3. Allow Gradle to sync dependencies.
4. Launch an Android Virtual Device (AVD) running **Android 10 (API 29)** or newer.
5. Click **Run 'app'** (`Shift + F10`).

> **Note on Android Networking:** The Android emulator uses `http://10.0.2.2:8080` as an alias for the host machine's `http://localhost:8080`. This is pre-configured in the debug build.

---

### Step 5: Building the Android APK & Bundle

You can generate the Android APK packages directly from the command line without opening Android Studio.

#### 1. Build Debug APK (Fast, unminified, debuggable)
Use this for local testing on emulators and physical test devices:

**On Windows (PowerShell):**
```powershell
cd android
.\gradlew.bat assembleDebug
```

**On Linux / macOS:**
```bash
cd android
./gradlew assembleDebug
```

📍 **Output file location:**
`android/app/build/outputs/apk/debug/app-debug.apk`

---

#### 2. Build Release APK (R8 Minified, ProGuard Optimized)
Applies full R8 code shrinking and resource optimization:

**On Windows (PowerShell):**
```powershell
cd android
.\gradlew.bat assembleRelease
```

**On Linux / macOS:**
```bash
cd android
./gradlew assembleRelease
```

📍 **Output file location:**
`android/app/build/outputs/apk/release/app-release.apk`

---

#### 3. Build Google Play Android App Bundle (.aab)
For publishing to the Google Play Store:

```bash
cd android
./gradlew bundleRelease       # Linux/macOS
.\gradlew.bat bundleRelease   # Windows
```

📍 **Output file location:**
`android/app/build/outputs/bundle/release/app-release.aab`

---

#### 4. Installing the APK on a Connected Device or Emulator
Ensure your phone has **USB Debugging** enabled, or start an Android Emulator:

```bash
# Verify device is connected
adb devices

# Install APK (the -r flag reinstalls while keeping existing app data)
adb install -r android/app/build/outputs/apk/debug/app-debug.apk
```
*(Alternatively, drag and drop the `.apk` file directly onto the running Android emulator window).*

---

#### 5. Building via Android Studio GUI
1. In Android Studio, go to the top menu: **Build** → **Build Bundle(s) / APK(s)** → **Build APK(s)**.
2. When the build completes, click the **locate** link in the popup balloon to open the folder containing `app-debug.apk`.

---

## 4. End-to-End Walkthrough via cURL / Postman

Here is a quick walkthrough to verify core banking workflows locally:

### 1. Register a New Customer
```bash
curl -X POST http://localhost:8080/api/v1/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "username": "alice",
    "email": "alice@banking.local",
    "password": "Password123!",
    "fullName": "Alice Smith",
    "deviceId": "device-client-1"
  }'
```
*Save the returned `accessToken` and `userId`.*

### 2. Login to Obtain Bearer JWT
```bash
curl -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "username": "alice",
    "password": "Password123!",
    "deviceId": "device-client-1"
  }'
```

### 3. Open a Checking Account (Requires Zero Opening Balance)
```bash
curl -X POST http://localhost:8080/api/v1/accounts \
  -H "Authorization: Bearer <ALICE_ACCESS_TOKEN>" \
  -H "Content-Type: application/json" \
  -d '{
    "customerId": "<ALICE_USER_ID>",
    "accountType": "CHECKING",
    "currency": "GBP",
    "initialDeposit": "0.0000"
  }'
```
*Save the generated `id` and `accountNumber`.*

### 4. Admin Teller Deposit (Funding the Account)
```bash
# Obtain Admin JWT token, then deposit:
curl -X POST http://localhost:8080/api/v1/accounts/<ACCOUNT_ID>/deposits \
  -H "Authorization: Bearer <ADMIN_ACCESS_TOKEN>" \
  -H "Content-Type: application/json" \
  -d '{
    "amount": "1000.0000",
    "reference": "CASH-BRANCH-DEPOSIT"
  }'
```

### 5. Execute an Idempotent Transfer
```bash
curl -X POST http://localhost:8080/api/v1/transfers \
  -H "Authorization: Bearer <ALICE_ACCESS_TOKEN>" \
  -H "Idempotency-Key: b7a1b41f-a3e9-4e67-897d-94c6d3dfef23" \
  -H "Content-Type: application/json" \
  -d '{
    "sourceAccountId": "<SOURCE_ACCOUNT_ID>",
    "destinationAccountId": "<DESTINATION_ACCOUNT_ID>",
    "amount": "150.0000",
    "currency": "GBP",
    "reference": "Rent Payment"
  }'
```

---

## 5. Running the Test Suites

### Backend Tests (JUnit 5 + Embedded PostgreSQL)
The backend tests run against an embedded PostgreSQL database, verifying concurrency, idempotency, and double-entry ledger reconciliation:
```bash
cd backend
# Run all tests
./gradlew test        # macOS/Linux
.\gradlew.bat test    # Windows

# View test report:
# backend/build/reports/tests/test/index.html
```

### Frontend Tests (Vitest)
```bash
cd web
# Run Vitest test suite
npm test -- --run

# Run TypeScript compilation and build
npm run build
```

### Automated Local Smoke Test Script
```powershell
# Windows PowerShell
.\infra\scripts\smoke-test.ps1 -BaseUrl "http://localhost:8080"
```
```bash
# Linux / macOS
chmod +x ./infra/scripts/smoke-test.sh
./infra/scripts/smoke-test.sh http://localhost:8080
```

---

## 6. Common Troubleshooting

| Issue | Cause | Resolution |
|---|---|---|
| `Port 5432 already in use` | Another PostgreSQL service is running locally | Stop existing Postgres (`Stop-Service postgresql*` or kill process) or change `DB_PORT: 5433` in `.env` / `docker-compose.yml`. |
| `Connection refused: localhost:8080` | Backend is still starting up or database connection failed | Verify PostgreSQL is healthy with `docker ps`. Inspect backend logs for connection errors. |
| `HTTP 429 Too Many Requests` | Sliding window rate limit exceeded | Wait 60 seconds for the sliding window to drain, or adjust rate limit in `application.yml`. |
| `HTTP 401 Unauthorized` | Missing or expired JWT token | Re-authenticate via `/api/v1/auth/login` or rotate token using `/api/v1/auth/refresh`. |
| Android emulator cannot reach backend | Host network misconfiguration | Ensure backend is listening on `0.0.0.0` or emulator is communicating via `10.0.2.2:8080`. |

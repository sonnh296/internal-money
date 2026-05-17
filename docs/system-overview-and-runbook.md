# Banking POS - System Overview and Runbook

## 1) System Overview

`banking-pos` is a microservice-based banking demo platform with independent services and databases.

### Core services

- `AuthUser` (`8094`): internal IAM, JWT issuer, refresh tokens, internal user provisioning.
- `CustomerService` (`8083`): customer profile, KYC lifecycle, customer existence checks.
- `AccountService` (`8084`): accounts, balances, holds, postings (credit/debit), transactions.
- `BillerService` (`8088`): biller registry per customer.
- `PaymentOrchestrator` (`8086`): bill-pay orchestration, hold placement, payment state, outbox.
- `BillPayWorkerService` (`8090`): batching/worker flow for bill-pay.
- `SettlementService`: settlement result persistence.
- `EFTService` + `EFTWorkerService`: EFT domain services.

Shared modules:
- `commons-security`: JWT/security helpers.
- `commons-dto`: event/request DTOs.
- `commons-observability`: common observability code.

### Database model

- PostgreSQL is used for all services.
- Each service has its own database (database-per-service):
  - `authdb`, `customerdb`, `accountsdb`, `billerdb`, `paymentdb`, `billpayworkerdb`, `settlementdb`, `eftdb`, `eftworkerdb`
- Service ownership is logical via IDs (`customerId`, `accountId`, `paymentId`) rather than cross-DB foreign keys.

### Frontend

- Folder: `banking-pos-frontend`
- Stack: `Vue 3 + TypeScript + Pinia + Vue Router + Axios + Vite`
- Includes:
  - Auth flow (login/refresh/logout)
  - Admin setup (create internal users)
  - Customer / Account / Biller / BillPay flows
  - API playground

## 2) Prerequisites

- JDK 21 (or project-compatible JDK)
- Maven 3.9+
- Node.js 22+
- Docker (for PostgreSQL)

## 3) Boot local infra

From repo root:

```bash
docker compose up -d postgres
```

Create required databases (one-time or idempotent):

```bash
for db in authdb customerdb accountsdb billerdb paymentdb billpayworkerdb settlementdb eftdb eftworkerdb; do
  docker exec docker-common-postgres psql -U postgres -tc "SELECT 1 FROM pg_database WHERE datname='${db}'" | grep -q 1 || \
  docker exec docker-common-postgres psql -U postgres -c "CREATE DATABASE ${db}";
done
```

### Reset local data (keep admin only)

When you need a clean slate for functional testing while preserving the seeded admin account:

```bash
chmod +x scripts/reset-local-data.sh
./scripts/reset-local-data.sh
```

This script:
- Drops and recreates service databases (`customerdb`, `accountsdb`, `billerdb`, `paymentdb`, `billpayworkerdb`, `settlementdb`, `eftdb`, `eftworkerdb`)
- Clears `authdb` non-admin users and refresh tokens
- Keeps `admin.demo@mockbank.local` so admin login remains available

## 4) Run backend services

Run each service in a separate terminal:

```bash
cd AuthUser && mvn spring-boot:run
cd CustomerService && mvn spring-boot:run
cd AccountService && mvn spring-boot:run
cd BillerService && mvn spring-boot:run
cd PaymentOrchestrator && mvn spring-boot:run
cd BillPayWorkerService && mvn spring-boot:run
cd SettlementService && mvn spring-boot:run
cd EFTService && mvn spring-boot:run
cd EFTWorkerService && mvn spring-boot:run
```

Notes:
- `AuthUser` (dev profile) seeds demo admin automatically via `DemoAdminSeeder`.
- Some workers/features may depend on Kafka for full async flow.

## 5) Run frontend

```bash
cd banking-pos-frontend
cp .env.example .env
npm install
npm run dev
```

Open: `http://localhost:5173`

## 6) Demo users

Default seeded admin (dev):

- email: `admin.demo@mockbank.local`
- password: `Admin@12345`
- customerId: `admin-root-0001`

You can also provision additional users in **Admin Setup** page.

## 7) Functional test checklist

- Auth: login, refresh, logout, secure endpoint access.
- Customer: create, get, exists, patch, KYC update.
- Account: create, list by customer, balance, credit/debit, holds, transactions.
- Biller/Payment: create/list/delete biller, create/get billpay.
- Validation: wrong password, unauthorized access, duplicate externalId, insufficient funds, malformed payload.

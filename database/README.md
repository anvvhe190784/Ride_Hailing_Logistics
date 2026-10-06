# Real-Time Ride Hailing & Logistics Database Architecture

## 1. Overview

This database schema is designed according to the software requirements specification (SRS-RHL-002 v1.0, IEEE 830-1998
standard) for the **Real-Time On-Demand Ride-Hailing & Logistics System**.

- **RDBMS Engine**: PostgreSQL 18.3+ with PostGIS 3.6+ and UUID-OSSP
- **Database Name**: `ride_hailing_db`
- **Default Connection**: `postgresql://postgres:admin@127.0.0.1:5432/ride_hailing_db`

## 2. Domain Schema Separation (CON-01, DR-001)

The database is structured into 7 distinct domain schemas to enforce Clean Architecture and Bounded Context boundaries:

| Schema     | Domain / Context             | Tables Included                                               | Key Purpose                                                       |
|------------|------------------------------|---------------------------------------------------------------|-------------------------------------------------------------------|
| `iam`      | Identity & Access Management | `users`                                                       | User credentials, roles (RBAC), and status                        |
| `driver`   | Driver & Fleet               | `driver_profiles`, `vehicles`, `driver_documents`             | Driver KYC, availability status, and vehicle records              |
| `location` | Telemetry & Spatial          | `driver_latest_locations`, `driver_telemetry_history`         | Real-time GPS coordinates with PostGIS GiST spatial indexing      |
| `pricing`  | Pricing & Surge Engine       | `pricing_rules`, `fare_quotes`                                | Configurable base/distance/time pricing and fare quotes           |
| `trip`     | Trip & Dispatch              | `trips`, `trip_stops`, `driver_offers`, `trip_status_history` | State machine, atomic matching, and driver exclusivity            |
| `billing`  | Payment & Wallet             | `payments`, `wallets`, `wallet_entries`, `refunds`            | Immutable financial ledger, commission, and payment attempts      |
| `platform` | Cross-Cutting & Reliability  | `outbox_events`, `audit_records`, `idempotency_records`       | Transactional outbox, immutable audit trails, and API idempotency |

## 3. Key Architectural Highlights

- **Spatial Indexing (`location.driver_latest_locations`)**: Utilizes PostGIS GiST indexing on `GEOMETRY(Point, 4326)`
  for sub-20ms spatial queries (`ST_DWithin`, `ST_Distance`).
- **Atomic Concurrency Guarantee (BR-002, CON-06)**: Uses a partial unique index on
  `trip.trips (driver_id) WHERE status IN ('ACCEPTED', 'PICKING_UP', 'ARRIVED', 'IN_TRIP')` preventing race conditions
  and double trip assignments.
- **Immutable Financial Ledger (FR-WAL-002, BR-011)**: Driver earnings, platform commissions, and adjustments are
  strictly recorded as immutable entries in `billing.wallet_entries`.
- **Transactional Outbox (`platform.outbox_events`)**: Guarantees at-least-once cross-service domain event delivery
  without distributed transactions.
- **Idempotency Guard (`platform.idempotency_records`)**: Enforces idempotency keys on all mutating endpoints.

## 4. Automation & Migration

Run the automated PowerShell runner:

```powershell
powershell -ExecutionPolicy Bypass -File .\setup_database.ps1
```

Or execute manual migration scripts in sequence:

1. `init_db.sql`
2. `migrations/01_extensions_and_schemas.sql`
3. `migrations/02_iam_and_driver.sql`
4. `migrations/03_location_and_pricing.sql`
5. `migrations/04_trip_and_dispatch.sql`
6. `migrations/05_billing_and_wallet.sql`
7. `migrations/06_platform_outbox.sql`
8. `tests/verify_db.sql`

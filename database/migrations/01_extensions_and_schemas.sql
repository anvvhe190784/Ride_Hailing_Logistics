-- ============================================================================
-- MIGRATION: 01_extensions_and_schemas.sql
-- PURPOSE: Initialize PostGIS, UUID extensions and Domain Schemas (CON-01, CON-04)
-- ============================================================================

-- Extensions
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "postgis";

-- Domain Schemas (Clean Bounded Context Separation per CON-01)
CREATE SCHEMA IF NOT EXISTS iam; -- Identity & Access Management
CREATE SCHEMA IF NOT EXISTS driver; -- Driver, Vehicle & Document Management
CREATE SCHEMA IF NOT EXISTS location; -- Telemetry, Realtime GPS & Spatial Indexes
CREATE SCHEMA IF NOT EXISTS pricing; -- Pricing Rules, Surge Snapshot & Fare Quotes
CREATE SCHEMA IF NOT EXISTS trip; -- Trip Lifecycle, State Machine & Dispatching
CREATE SCHEMA IF NOT EXISTS billing; -- Payments, Driver Wallets & Immutable Ledger
CREATE SCHEMA IF NOT EXISTS platform; -- Outbox Events, Audit Trail & Idempotency

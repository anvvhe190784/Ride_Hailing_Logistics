# Ride-Hailing & Logistics Backend Platform

A production-grade microservices backend platform for ride-hailing and logistics based on **SRS-RHL-002 v1.0**.

Built with **Java 21 LTS**, **Spring Boot 3.4.3**, **Spring Cloud 2024.0.0**, **PostgreSQL 18 + PostGIS**, **Apache
Kafka (KRaft)**, and **Redis 7.2**.

---

## Architecture Overview

```
                      +-------------------+
                      |   Client Apps     |
                      +---------+---------+
                                | :8080
                      +---------v---------+
                      |    API Gateway    |  (Spring Cloud Gateway + Centralized JWT Filter)
                      +----+---------+----+
                           |         |
          +----------------+         +----------------+
          |                                           |
+---------v---------+                       +---------v---------+
|    IAM Service    | (:8081)               |  Driver Service   | (:8082)
|  Auth, JWT, RBAC  |                       | KYC, Vehicles     |
+-------------------+                       +-------------------+
          |                                           |
+---------v---------+                       +---------v---------+
| Location Service  | (:8083)               |  Pricing Service  | (:8084)
| Telemetry, PostGIS|                       | Dynamic Fare Calc |
+-------------------+                       +-------------------+
          |                                           |
+---------v-------------------------------------------v---------+
|                         Trip Service                          | (:8085)
| State Machine, OpenFeign + Resilience4j, Redis Lock, Producer |
+---------------------------------+-----------------------------+
                                  | Kafka: trip.completed
                        +---------v---------+
                        |  Payment Service  | (:8086)
                        | Wallets & Ledger  |
                        +-------------------+

Infrastructure & Support:
- Discovery Service (:8761): Netflix Eureka Service Registry
- Config Service (:8888): Centralized External Configuration
```

---

## Tech Stack & Domain Coverage

| Area                            | Technology                                                       |
|---------------------------------|------------------------------------------------------------------|
| **Runtime & Language**          | Java 21 LTS, Gradle 8.12 Multi-Project                           |
| **Framework**                   | Spring Boot 3.4.3, Spring Cloud 2024.0.0                         |
| **Service Discovery & Config**  | Netflix Eureka Server, Spring Cloud Config Server                |
| **Gateway & Security**          | Spring Cloud Gateway, Spring Security 6, JWT (HMAC-SHA256), RBAC |
| **Inter-Service Communication** | Spring Cloud OpenFeign + Resilience4j Circuit Breaker            |
| **Event Streaming**             | Apache Kafka (Confluent 7.6.1 KRaft Mode)                        |
| **Caching & Locking**           | Redis 7.2 (Lettuce, RedisTemplate distributed lock `SET NX PX`)  |
| **Database & GIS**              | PostgreSQL 18 with PostGIS 3.6 spatial extension                 |
| **Data Access & Mapping**       | Spring Data JPA, Hibernate Spatial, JTS, MapStruct, Lombok       |

---

## Database Schemas & Migrations

The database contains 7 bounded-context schemas and 19 tables in `database/migrations`:

- `iam`: `users`, `roles`, `refresh_tokens`, `audit_logs`
- `driver`: `driver_profiles`, `vehicles`, `driver_documents`, `driver_activity_logs`
- `location`: `driver_current_locations`, `location_history` (PostGIS geometry)
- `pricing`: `pricing_rules`, `fare_quotes`, `surge_multipliers`
- `trip`: `trips`, `trip_stops`, `driver_offers`, `trip_status_history`
- `billing`: `wallets`, `wallet_entries`, `payment_transactions`
- `platform`: `system_configurations`

---

## Quickstart

### 1. Start Infrastructure via Docker Compose

```bash
cd backend
docker compose up -d
```

### 2. Verify Database Setup

```powershell
cd ../database
.\setup_database.ps1
```

### 3. Build & Test Microservices

```bash
cd ../backend
./gradlew build
```

### 4. Run Services

Start the service discovery and config first:

```bash
./gradlew :discovery-service:bootRun
./gradlew :config-service:bootRun
```

Then start the business services and gateway:

```bash
./gradlew :api-gateway:bootRun
./gradlew :iam-service:bootRun
./gradlew :driver-service:bootRun
./gradlew :location-service:bootRun
./gradlew :pricing-service:bootRun
./gradlew :trip-service:bootRun
./gradlew :payment-service:bootRun
```

---

## License

MIT

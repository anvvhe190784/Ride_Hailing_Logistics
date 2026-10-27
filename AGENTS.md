# Project Engineering & Architecture Directives (Ride-Hailing & Logistics)

This document establishes the mandatory engineering standards, architectural constraints, and production-readiness rules for the **Ride-Hailing & Logistics Multi-Microservices Platform**. All developers, subagents, and AI assistants MUST strictly adhere to these directives across all modules.

---

## 1. Architecture, Layering & Network Resilience

1. **Strict Layer Boundary & Call Hierarchy:**
   - Call chains MUST follow: `Controller` $\rightarrow$ `Service` $\rightarrow$ `Repository`.
   - Controllers MUST NOT call Repositories directly.
   - Cross-module or cross-service circular dependencies are STRICTLY PROHIBITED.
2. **DTO & Record Encapsulation (Zero Entity Leaks):**
   - Controllers and Kafka Event Producers MUST NOT accept, return, or serialize JPA `@Entity` objects.
   - All external API payloads and event messages MUST use dedicated Java `record` or DTO classes.
   - Entity-to-DTO conversion MUST happen via MapStruct or explicit mapper methods to eliminate `LazyInitializationException` and database schema exposure.
3. **Hard Network Timeouts:**
   - All external network calls (OpenFeign, `RestClient`, `WebClient`, gRPC, 3rd-party payment gateways) MUST have explicit, hard-coded timeouts:
     - `connectTimeout`: Maximum `3000ms`.
     - `readTimeout`: Maximum `5000ms` (or `10000ms` for long-running payment/banking webhooks).
   - Ingress/Gateway `response-timeout` MUST be strictly higher than downstream service timeouts to prevent hanging `504 Gateway Timeout` socket leaks.
4. **Circuit Breakers & Graceful Degradation:**
   - Every inter-service OpenFeign client MUST be protected by a Resilience4j Circuit Breaker with a documented Fallback Bean.
   - When downstream services fail or latency spikes, Fallback implementations MUST return graceful degradation responses (e.g., cached quotes, default estimates, or standard domain rejection codes).
5. **Frontend Security (When Applicable):**
   - Rendering strategy MUST match data sensitivity: SSR/SSG for public SEO-facing pages, CSR for authenticated portals.
   - Secrets, private API tokens, and internal service URLs MUST NEVER be prefixed with public env variables (`NEXT_PUBLIC_`, `VITE_`).

---

## 2. Data Integrity, Concurrency & Distributed Consistency

1. **Database Transaction Boundaries:**
   - Any business use case performing write or update operations across multiple tables or entities MUST be encapsulated within `@Transactional`.
   - Read-only operations MUST use `@Transactional(readOnly = true)` to avoid Hibernate dirty-checking overhead and enable read-replica routing.
2. **Remote I/O Isolation (No Long-Holding Transactions):**
   - Network calls (HTTP Feign, REST calls, SMS/Email sending, Kafka event publishing, Redis caching) MUST NOT be executed inside `@Transactional` methods.
   - Keep database transactions as short as possible ($< 50\text{ms}$) to prevent HikariCP connection exhaustion.
3. **Concurrency Control & Race Condition Defenses:**
   - Critical operations (fare deduction, wallet top-up, trip claiming, driver dispatch, booking confirmation) MUST use appropriate locking:
     - **Pessimistic Lock (`SELECT ... FOR UPDATE`):** When contention is high and immediate reservation is required.
     - **Optimistic Lock (`@Version`):** For wallet balance updates and driver state transitions with low contention.
     - **Atomic Updates (CAS):** Conditional SQL updates (`UPDATE ... SET status = 'ACCEPTED' WHERE id = :id AND status = 'PENDING'`).
4. **Distributed Lock Safety (Redis / Redisson):**
   - When using Redis locks, systems MUST:
     1. Set an explicit Time-To-Live (TTL, e.g., 5–10s) to prevent permanent deadlocks on crashed nodes.
     2. Assign a unique owner token (e.g., `UUID`) and release locks ONLY via atomic Lua script matching the token.
     3. Provide a database Unique Constraint or state check as the ultimate fallback if lock TTL expires prematurely.
5. **Dual-Write Prevention & Transactional Outbox Pattern:**
   - Systems MUST NOT write to the database and publish directly to Kafka/RabbitMQ in an uncoordinated manner.
   - Use the **Transactional Outbox Pattern**: Record outbox events in `platform.outbox_events` inside the SAME database transaction as the business entity.
   - A dedicated background publisher job with `FOR UPDATE SKIP LOCKED` MUST poll and dispatch outbox events with at-least-once delivery guarantees.
   - Alternatively, `@TransactionalEventListener(phase = AFTER_COMMIT)` may trigger immediate non-blocking dispatch with fallback to outbox recovery.
6. **State Machine Integrity & Timeout Cleaners:**
   - Entities with lifecycles (Trips, Orders, Payments, Offers) MUST NOT remain trapped in transitional states (`MATCHING`, `PENDING`, `PICKING_UP`).
   - Scheduled cleanup jobs (Timeout Cleaners) MUST poll stale records and transition them to terminal states (`CANCELLED`, `EXPIRED`, `FAILED`).
   - Every state machine MUST provide an Emergency Cancel / Rollback path.

---

## 3. Performance, Database, Indexing & Caching

1. **Schema vs Entity Parity (Zero Drift):**
   - JPA entity properties, column names, nullability, and precision (`NUMERIC(15, 2)`) MUST match database DDL migration scripts 1-to-1.
   - Hibernate DDL auto MUST be set to `validate` in all environments (`ddl-auto: validate`).
2. **Lean & Complete Indexing:**
   - All Foreign Keys (FK) and frequent query columns (`status`, `created_at`, `tenant_id`, geospatial points) MUST have indexes.
   - Zero-downtime production migrations MUST use `CREATE INDEX CONCURRENTLY IF NOT EXISTS` to avoid exclusive `ShareLock` table blocking.
   - Avoid creating redundant standalone indexes on columns already covered by `UNIQUE` constraints.
3. **N+1 Query Elimination & Bounded Pagination:**
   - Relational `@OneToMany` and `@ManyToMany` mappings MUST use `FetchType.LAZY`.
   - Avoid N+1 queries using `JOIN FETCH`, `@EntityGraph`, or DTO interface projections.
   - All list endpoints MUST enforce pagination (`Pageable`) with a strict global maximum page size:
     `spring.data.web.pageable.max-page-size: 100` and `default-page-size: 20` to eliminate Out-Of-Memory (OOM) risks.
4. **Connection Pool Balancing:**
   - HikariCP pool sizes (`maximum-pool-size`) across all service replicas MUST NOT exceed the database server's `max_connections`.
   - Standard baseline: `maximum-pool-size: 15`, `minimum-idle: 5` per microservice instance, allowing safe 2–3 pod horizontal scaling without connection rejection.
5. **Safe Caching Strategies:**
   - Cache high-frequency, low-mutability reads (pricing rules, driver metadata) with bounded TTLs (e.g., 60s to 10m).
   - Invalidate cache entries immediately upon entity mutations (`@CacheEvict` or Redis key deletion).
   - Defend against **Cache Stampede** by adding randomized TTL Jitter ($\pm 10\%$) and mutex locking.
   - Defend against **Cache Penetration** by caching empty/null sentinels with short TTL (e.g., 30s) or Bloom filters.

---

## 4. Security, Access Control & Gateway Hardening

1. **Token Lifecycle & Type Segregation:**
   - Access tokens MUST have short lifespans (e.g., 15–30 minutes).
   - JWT tokens MUST distinguish `type: ACCESS` and `type: REFRESH`. Refresh tokens MUST NOT be accepted on business resource endpoints.
   - Token revocation (logout or password reset) MUST blacklist tokens in Redis with an expiry matching token TTL.
2. **Role-Based Access Control (RBAC):**
   - Protect all privileged endpoints with `@PreAuthorize("hasRole('ADMIN')")` or gateway filters.
   - Public registration endpoints MUST strictly validate input roles and MUST NEVER allow clients to self-assign `ADMIN` or internal system roles.
3. **BOLA / IDOR Ownership Verification:**
   - Every API modifying or retrieving resources by ID (`/api/v1/trips/{id}`, `/wallets/{id}`) MUST verify resource ownership against the authenticated caller (`currentUserId` or `tenantId`).
   - Returning `403 FORBIDDEN` or `404 NOT FOUND` for unowned resources is mandatory to prevent unauthorized data enumeration.
4. **Input Sanitization & Gateway Header Stripping:**
   - All request payloads MUST be validated using Bean Validation (`@Valid`, `@NotNull`, `@Size`, `@Positive`).
   - API Gateway MUST strip client-supplied internal identity headers (`X-User-Id`, `X-User-Role`, `X-Tenant-Id`) before forwarding requests to backend microservices.
5. **CORS, Security Headers & Rate Limiting:**
   - CORS `allowedOrigins` MUST be restricted to explicit trusted domains; wildcard `*` with credentials is strictly prohibited.
   - Security headers MUST be enforced: `X-Frame-Options: DENY`, `X-Content-Type-Options: nosniff`, `Strict-Transport-Security`.
   - Rate limiting MUST protect brute-force targets (`/api/v1/auth/login`, OTP generation, ride requests, payment triggers).
6. **Account Lockout & Brute-Force Defense:**
   - Consecutive failed login attempts MUST increment an attempt counter outside the main transaction.
   - Accounts MUST be automatically locked (e.g., 15 minutes) after 5 consecutive failed attempts.
7. **Webhook Security & PII Protection:**
   - Inbound payment and partner webhooks MUST verify cryptographic signatures (HMAC-SHA256).
   - Personally Identifiable Information (PII) such as phone numbers, citizen IDs, and hashed passwords MUST be redacted or masked before returning in public APIs or logging to disk.

---

## 5. Fault Tolerance, Idempotency & Asynchronous Queues

1. **End-to-End Idempotency:**
   - All state-mutating requests (trip creation, payment processing, wallet crediting) MUST support an `Idempotency-Key` header.
   - Consumer event handlers MUST maintain an idempotency table or unique database constraint to prevent duplicate processing from Kafka retries.
2. **Queue Error Handling & Exponential Backoff:**
   - Queue consumers MUST NOT silently swallow exceptions in raw `catch (Exception ex)` blocks without re-throwing or tracking failure state.
   - Use `DefaultErrorHandler` with `ExponentialBackOffWithMaxRetries` (e.g., initial 1000ms, multiplier 2.0, max 3 retries) and randomized jitter to prevent retry storms against the database.
3. **Dead Letter Queue (DLQ / DLT):**
   - Poison pill messages failing after maximum retries MUST be routed to a dead letter topic (`<topic>.DLT`).
   - DLT partition resolvers MUST use `-1` or key-based distribution to avoid `UnknownTopicOrPartitionException` when partition counts differ.
4. **Consumer Prefetch & Concurrency Bounds:**
   - Kafka listener containers MUST constrain `max-poll-records` (e.g., 50) and concurrency (e.g., 2–3) to prevent consumer JVM memory spikes during traffic surges.

---

## 6. SRE, Background Jobs, Observability & Probes

1. **Distributed Cronjob Synchronization (ShedLock):**
   - Scheduled tasks (`@Scheduled`) in multi-instance environments MUST use distributed locking (`@SchedulerLock` with `LockProvider`).
   - Configure `lockAtLeastFor` and `lockAtMostFor` to guarantee single-node execution and prevent task overlap.
2. **Graceful Shutdown & Connection Draining:**
   - All services MUST configure graceful shutdown:
     ```yaml
     server:
       shutdown: graceful
     spring:
       lifecycle:
         timeout-per-shutdown-phase: 30s
     ```
   - This ensures in-flight requests and active transactions finish cleanly before the container terminates.
3. **Kubernetes Health Probes Separation (Cascading Restart Prevention):**
   - Enable Spring Boot Actuator probes: `management.endpoint.health.probes.enabled: true`.
   - **Liveness Probe (`/actuator/health/liveness`):** MUST check ONLY `livenessState` (process health). It MUST NOT check external dependencies (DB/Kafka/Redis) to avoid cascading restart storms when infrastructure suffers transient latency.
   - **Readiness Probe (`/actuator/health/readiness`):** MUST verify infrastructure connectivity (`readinessState`, `db`, `ping`, `diskSpace`) before the pod accepts live traffic.
4. **Distributed Tracing & Structured Logging:**
   - Every request MUST carry a `Correlation-ID` / `Trace-ID` propagated from the API Gateway down to the backend services.
   - Include `[%X{correlationId:-}]` in log patterns:
     ```yaml
     logging:
       pattern:
         console: "%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %-5level %logger{36} [%X{correlationId:-}] - %msg%n"
     ```
   - Global exception handlers MUST format error responses into standardized JSON (RFC 7807 `ProblemDetail` or `ApiResponse`) and suppress internal stack traces in production environments.
5. **JVM DNS Cache TTL:**
   - Containers MUST override the default infinite JVM DNS cache TTL by setting `networkaddress.cache.ttl=30s` to promptly detect IP changes of downstream pods in Kubernetes.

---

## 7. Domain-Specific Adaptations (Conditional Rules)

1. **SaaS Multi-tenant Systems:**
   - Every query (SELECT, INSERT, UPDATE, DELETE) MUST be scoped with `WHERE tenant_id = :tenantId`.
   - Use Hibernate filters, Spring Data JPA specifications, or database Row-Level Security (RLS) to enforce strict tenant boundary isolation.
2. **File Processing & Large Exports:**
   - Enforce upload file size caps (`spring.servlet.multipart.max-file-size: 10MB`).
   - File parsing and report generation MUST use streaming or chunking (e.g., SAX/StAX for XML, POI Streaming for Excel, reactive streams); NEVER load multi-hundred-megabyte files into heap RAM.
3. **Real-time Telemetry & WebSockets:**
   - Implement connection lifecycle listeners (heartbeats, ping/pong) to terminate abandoned connections and prevent connection leaks.
   - Redis Pub/Sub adapters MUST be used to synchronize broadcast events and location streams across distributed server instances.
4. **Serverless Architecture (Lambda / Vercel):**
   - When deploying serverless functions, database connections MUST be managed through connection poolers (PgBouncer, AWS RDS Proxy) to prevent DB connection saturation during cold-start concurrency spikes.
   - Keep execution timeouts lean and avoid heavy reflection or unneeded framework initialization.

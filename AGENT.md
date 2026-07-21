# AI Agent Development Guidelines (AGENT.md)

This document defines the rules of engagement and coding guidelines for AI assistant agents (like Antigravity) working on the **data-platform-sandbox** codebase.

---

## 🛠️ Rules of Engagement

### 1. Review-Driven Development (RDD) — MANDATORY
* **Plan Before Action:** You **must** create or update the `implementation_plan.md` artifact and present it to the user before making any changes to the codebase (source code, tests, or config files).
* **Explicit Approval Required:** Do not perform code modifications (reads are allowed) until the user has explicitly approved the plan.
* **Refining Plans:** If design issues arise during development, pause, update the implementation plan, and ask for review again.

### 2. Language Policy — MANDATORY
* **All English Content:** Every single file, code comment, commit message, documentation, pull request description, feature file, or configuration added to this repository must be written strictly in **English**.

### 3. Architecture Constraints (Hexagonal & DDD)
All new components and refactorings must follow the package structure under namespace `com.telecom.analytics.platform` defined in [README.md](file:///c:/dev/workspace/data_engineer/data-platform-sandbox/README.md) and the decisions documented in [ADR 0003](file:///c:/dev/workspace/data_engineer/data-platform-sandbox/docs/adr/0003-hexagonal-architecture-ddd-solid.md) and [ADR 0004](file:///c:/dev/workspace/data_engineer/data-platform-sandbox/docs/adr/0004-docker-compose-testcontainers-bdd.md):

* **Pure Domain Core:** 
  - Code in `domain/` must have **zero framework dependencies** (no Spring annotations, `@Service`, `@Autowired`, `@Repository`, or database persistence annotations like `@Document` or `@Entity`).
  - Invariants and business logic validation must live inside the domain entities (`domain/model/`).
* **Ports and Adapters:**
  - Define interfaces in the domain or application packages.
  - Implement concrete databases (PostgreSQL, MongoDB) and delivery systems (SQS listeners, REST) in `infrastructure/adapter/`.
* **Clean Code & SOLID:**
  - Keep classes small, focused on a single responsibility.
  - Rely on dependency inversion (injecting port interfaces rather than concrete infrastructure classes).

### 4. Concurrency, Ingest & Performance Rules
* **Idempotency:** Telemetry ingestion must be fully idempotent by checking unique event IDs against MongoDB before inserting.
* **Optimistic Locking:** Handle multi-process concurrency in MongoDB using version checks (`@Version` on documents), with retry/backoff policies.
* **Trino Catalog Integration:** Ensure PostgreSQL schemas (relational dimensions) and MongoDB schemas (events) align so Trino can execute federated `JOIN` operations cleanly.
* **JNI/FFI Performance:** Keep Java-to-C++ interaction isolated. Native arrays or structures used for performance optimization must manage memory manually and avoid GC stops.

---

## 🧪 Testing & Verification

* **BDD/Spec-Driven Development:** Write BDD tests in Gherkin (`src/test/resources/features/`). Implement step definitions using Testcontainers for integration tests.
* **Unit Testing:** Write fast in-memory unit tests for domain classes without launching Spring ApplicationContexts.

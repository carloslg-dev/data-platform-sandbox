# Case Study: Mobile Network Telemetry & Data Platform

## 1. Executive Summary & Purpose

This document establishes the reference architecture, data model, concurrency strategy, and design patterns for a high-throughput mobile network telemetry ingestion and analytics platform. It is designed as a reusable case study for technical portfolios and architecture demonstrations at a Senior Backend Developer and Solution Architect level, serving as a technical showcase and testing sandbox for simulated distributed environments.

---

## 2. Business Context & Functional Requirements

The system simulates the analytical core of a Customer Experience Management (CEM) platform for a telecommunications carrier, addressing the following key use cases:

* **Saturation Monitoring:** Analyzing antenna utilization at the cell level to detect saturated cells (requiring additional infrastructure deployments or traffic rebalancing) or underutilized cells (for power optimization and shutdown during off-peak hours).
* **Hourly Sizing:** Evaluating availability times, theoretical capacity (bandwidth and simultaneous channels), and peak/off-peak traffic windows.
* **Massive Event Ingestion:** Automated processing of large volumes of network events (voice calls and data sessions) with analytics latency tolerances greater than one second (prioritizing overall throughput and ingestion pipeline stability over strict real-time constraints).

---

## 3. Architecture & Immutable Data Model

To guarantee ultra-fast writes and prevent database locking due to contention, the system adopts an immutable, append-only event data modeling strategy:

### 3.1. Collection & Table Structures (PostgreSQL / MongoDB)

| Entity / Collection | Type | Key Attributes | Architectural Purpose |
| :--- | :--- | :--- | :--- |
| **`antennas`** | Dimension (Master) | `id`, `location`, `type`, `theoretical_capacity`, `status` | Static network infrastructure catalog. Stored in a relational database (PostgreSQL). |
| **`antenna_events`** | Facts (Immutable) | `event_id`, `timestamp`, `antenna_id`, `event_type` (`VOICE_CALL`, `DATA_SESSION`), `duration_ms`, `bytes_transferred`, `version` | Pure network telemetry. Massively written sequentially with no updates to prevent write contention and lock overhead. |

---

## 4. Ingestion, Concurrency, and Resilience Strategy

* **Decoupling with Message Queues:** To absorb extreme traffic peaks (e.g., up to 50,000 events/second in production environments), an **AWS SQS FIFO** queue (or LocalStack simulation in development) is used to decouple telemetry event producers from downstream persistency workers.
* **Delivery Guarantees & Exactly-Once Processing:** SQS FIFO guarantees *at-least-once* delivery. The *exactly-once* effect is achieved via **application-level idempotency**, checking for the existence of the unique `event_id` hash in MongoDB before persisting the record.
* **Concurrency Control (Optimistic Locking):** In scenarios where multiple processes attempt to modify status/version records concurrently, the system uses Spring Data MongoDB's `@Version` annotation. If an `OptimisticLockingFailureException` is thrown, the service layer executes a retry strategy with exponential backoff.

---

## 5. Integration with Distributed Query Engines (Trino)

For the analytical layer to run complex queries without impacting operational ingestion datastores:

* **Role of Trino:** A distributed in-memory SQL query engine that stores no data itself but connects to PostgreSQL and MongoDB via catalog configurations (`postgresql.properties` and `mongodb.properties`).
* **Cross-Catalog JOINs:** Enables standard SQL queries joining relational tables (static antennas in PostgreSQL) with document streams (telemetry logs in MongoDB). For example: identifying antennas whose total traffic volume exceeds 80% of their theoretical capacity.
* **Architectural Challenge & Native C++ Optimization:** Because Trino runs on the JVM, massive memory allocations can trigger significant Garbage Collection (Stop-the-World) pauses. To mitigate critical latency peaks, heavy computational operations and array parsing are delegated to **native C++ modules** via Foreign Function Interfaces (FFI/JNI), managing memory manually using pointers.

---

## 6. Development & Local Sandbox Strategy

To validate the architecture while optimizing hardware resources and developer workflow:

* **Hybrid Testcontainers / Docker Compose Approach:**
  - **Static Docker Compose:** Used in local development environments to run persistent services in the background (Trino, MongoDB, PostgreSQL, LocalStack), enabling interactive testing and execution plan validation.
  - **Testcontainers:** Utilized during automated integration builds to spin up clean, ephemeral database containers for Spring Data repositories, ensuring test repeatability.
* **Spec-Driven Development (SDD):** Development is guided by Gherkin (Cucumber) specifications, strict domain invariant definitions (DDD), and hexagonal packaging, orchestrating the system under tech lead supervision.

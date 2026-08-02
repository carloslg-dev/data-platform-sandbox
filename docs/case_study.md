# Case Study: Mobile Network Telemetry & Decoupled Data Platform

## 1. Executive Summary & Purpose

This document establishes the authoritative reference architecture, data model, event-driven messaging strategy, concurrency controls, and design patterns for a high-throughput mobile network telemetry ingestion and analytics platform. 

It serves as the **Single Source of Truth (SSOT)** for the system, demonstrating distributed SQL engine federation (Trino), polyglot persistence (PostgreSQL, MongoDB, MinIO/S3 Data Lake), asynchronous event-driven messaging (Apache Kafka KRaft mode & AWS SQS), Hexagonal Architecture (Ports and Adapters), and native C++ optimizations for Senior Backend & Data Engineering reference architectures.

---

## 2. Business Context & Functional Requirements

The system simulates the analytical core of a Customer Experience Management (CEM) platform for a telecommunications carrier, addressing three primary operational use cases:

* **Real-Time Saturation Alerting:** Monitoring cell tower bandwidth utilization to detect congested cells (`capacityPercentage > 80%`) in real time, emitting automated network alert events to trigger traffic rebalancing or infrastructure provisioning.
* **Polyglot Storage & Retention Management:** Separating master data catalogs, hot operational status, and cold historical telemetry into tailored storage engines based on query patterns, access latency, and data lifecycle requirements.
* **Massive Decoupled Event Ingestion:** Processing continuous streams of network telemetry (voice calls and data sessions) with guaranteed per-cell chronological ordering and non-intrusive historical replay capabilities for audit and monitoring tools.

---

## 3. Architecture & Polyglot Persistence Strategy

The system enforces strict Hexagonal Architecture (DDD) boundaries to isolate core domain business logic (`ProcessTelemetryUseCase`) from infrastructure adapters (Kafka, SQS, PostgreSQL, MongoDB, MinIO/S3, Trino).

```text
                                  [ Inbound Layer ]
                                          │
                    ┌─────────────────────┴─────────────────────┐
                    ▼                                           ▼
         RestTelemetryController                     KafkaTelemetryConsumerAdapter
           (Synchronous HTTP)                               (@KafkaListener)
                    │                                           │
                    └─────────────────────┬─────────────────────┘
                                          ▼
                       [ Inbound Port: ProcessTelemetryUseCase ]
                                          │
                              [ Core Domain Model ]
                         (Antenna & Telemetry Event)
                                          │
                       [ Outbound Ports Repository Interfaces ]
                                          │
          ┌───────────────────────────────┼───────────────────────────────┐
          ▼                               ▼                               ▼
PostgresCatalogPort              MongoTelemetryPort              DataLakeStoragePort
(PostgresAntennaAdapter)     (MongoAntennaEventAdapter)         (S3ParquetDataAdapter)
          │                               │                               │
          ▼                               ▼                               ▼
   [ PostgreSQL DB ]                 [ MongoDB ]                   [ MinIO / S3 ]
   (Master Catalog)               (Operational State)             (Parquet Data Lake)
          ▲                               ▲                               ▲
          └───────────────────────────────┴───────────────────────────────┘
                                          │
                                   [ Trino Engine ]
                             (Federated SQL Analytics)
```

### 3.1. Polyglot Storage Engine Breakdown

| Storage Engine | Engine Type | Key Data Schema / Format | Primary Architectural Responsibility | Access Mechanism / Query Engine |
| :--- | :--- | :--- | :--- | :--- |
| **PostgreSQL** | Relational (ACID) | Normalized tables (`antennas`, `cells`, `locations`) | Master Data & Static Network Catalog (theoretical capacity limits, GPS coordinates, hardware specifications). | Spring Data JPA / Direct SQL |
| **MongoDB** | Document NoSQL | Semi-structured BSON documents (`antenna_events`, `antenna_status`) | Hot/Warm operational state, fast dashboard reads, cell-level usage aggregations, optimistic locking (`@Version`). | Spring Data Mongo |
| **MinIO / S3** | Object Store | Immutable Parquet columnar files (`s3://telemetry-lake/raw-events/`) | Cold storage Data Lake for historical telemetry retention (> 1 year). | **Trino** (via Hive/Iceberg connector) |

---

## 4. Event-Driven Messaging & Kafka KRaft Architecture

To decouple telemetry event ingestion from downstream persistence workers and enable multi-consumer group fanout, the platform adopts **Apache Kafka in KRaft mode (ZooKeeper-less)** alongside AWS SQS FIFO.

```text
Producer ──> Kafka Topic: telemetry.antenna.raw (Partitioned by cell_id)
                 │
                 ├── Consumer Group: cg-telemetry-ingestion   ──> Persists to MongoDB
                 ├── Consumer Group: cg-telemetry-monitoring  ──> Emitsalerts (>80%)
                 └── Consumer Group: cg-telemetry-audit       ──> Replays from offset 0
```

### 4.1. Key-Based Routing & Ordering Invariant
* **Partition Key (`cell_id` / `antenna_id`)**: Emitted telemetry records use `cell_id` as the partition key. MurmurHash2 key routing guarantees that all events from a given cell tower land on the **exact same Kafka partition**, ensuring strict FIFO chronological processing per cell.
* **Consumer Group Fanout**:
  - `cg-telemetry-ingestion`: Ingests raw telemetry events, validates domain invariants, and updates MongoDB operational state.
  - `cg-telemetry-monitoring`: Evaluates real-time congestion and publishes alerts to `telemetry.antenna.alerts` when `capacityPercentage > 80%`.
  - `cg-telemetry-audit`: Set with `auto.offset.reset = earliest` to replay historical logs without affecting production state.

### 4.2. Delivery Guarantees & Application-Level Idempotency
* **At-Least-Once Delivery**: Kafka producers use `acks = all` (`acks = -1`) and `enable.idempotence = true`. Consumers commit offsets manually (`AckMode.MANUAL_IMMEDIATE`) after successful database writes.
* **Consumer-Side Idempotency**: To prevent duplicate processing during network retries or partition rebalances, MongoDB enforces a **unique index on `eventId`**, safely rejecting duplicate writes.
* **Poison Pill Protection**: Malformed payloads are intercepted by `ErrorHandlingDeserializer` and routed to a Dead Letter Topic (`telemetry.antenna.raw.DLT`).

---

## 5. Integration with Distributed Query Engines (Trino)

For complex analytical queries without impacting operational databases:

* **Role of Trino**: Massively Parallel Processing (MPP) SQL query engine connecting to PostgreSQL, MongoDB, and MinIO S3 Data Lake via catalog properties (`postgresql.properties`, `mongodb.properties`, `datalake.properties`).
* **Federated Cross-Catalog Queries**: Executes standard SQL joins combining relational static catalogs (PostgreSQL `antennas`), operational document facts (MongoDB `antenna_events`), and historical Data Lake Parquet files (MinIO `raw_events`).
* **Native C++ Performance Optimization (JNI / Panama FFM)**: To prevent JVM Garbage Collection (STW) pauses during heavy numerical calculations, mathematical aggregations (standard deviation and traffic mean calculations) can be offloaded to **native C++ modules** via Foreign Function Interfaces.

---

## 6. Architecture Decision Records (ADRs) Summary

The architectural evolution of this platform is recorded in formal ADR documents:
- [ADR 0001: Record Architecture Decisions](file:///c:/dev/workspace/data_engineer/data-platform-sandbox/docs/adr/0001-record-architecture-decisions.md)
- [ADR 0002: Decoupled Ingestion Pipeline Architecture](file:///c:/dev/workspace/data_engineer/data-platform-sandbox/docs/adr/0002-decoupled-ingestion-pipeline.md)
- [ADR 0003: Hexagonal Architecture, DDD & SOLID Principles](file:///c:/dev/workspace/data_engineer/data-platform-sandbox/docs/adr/0003-hexagonal-architecture-ddd-solid.md)
- [ADR 0004: Hybrid Docker Compose & Testcontainers Sandbox BDD Strategy](file:///c:/dev/workspace/data_engineer/data-platform-sandbox/docs/adr/0004-docker-compose-testcontainers-bdd.md)
- [ADR 0005: Apache Kafka KRaft Event Bus & MinIO Parquet Data Lake Integration](file:///c:/dev/workspace/data_engineer/data-platform-sandbox/docs/adr/0005-event-driven-kafka-kraft-datalake.md)

# Technical Architecture Specification: Decoupled Telemetry Platform

## 1. Architectural Overview
This platform implements an **Event-Driven Architecture (EDA)** guided by **Hexagonal Architecture (Ports and Adapters)** and **Spec-Driven Development (SDD)** principles. The system ingests, processes, monitors, and persists high-throughput mobile network antenna telemetry in real time.

The core domain business logic remains strictly decoupled from transport mechanisms (REST HTTP vs. Kafka messaging) and infrastructure persistence engines (PostgreSQL, MongoDB, MinIO/S3 Data Lake).

```text
               ┌────────────────────────────────────────────────────────┐
               │                    Inbound Layer                       │
               └───────────────┬────────────────────────┬───────────────┘
                               │                        │
                               ▼                        ▼
                   RestTelemetryController   KafkaTelemetryConsumerAdapter
                     (Synchronous HTTP)         (@KafkaListener)
                               │                        │
                               └───────────┬────────────┘
                                           ▼
                       [ Inbound Port: ProcessTelemetryUseCase ]
                                           │
                                 [ Core Domain Model ]
                             (Antenna & Telemetry Event)
                                           │
                        [ Outbound Ports Repository Interfaces ]
                                           │
             ┌─────────────────────────────┼─────────────────────────────┐
             ▼                             ▼                             ▼
   PostgresCatalogPort            MongoTelemetryPort           DataLakeStoragePort
 (PostgresAntennaAdapter)     (MongoAntennaEventAdapter)      (S3ParquetDataAdapter)
             │                             │                             │
             ▼                             ▼                             ▼
      [ PostgreSQL DB ]               [ MongoDB ]                 [ MinIO / S3 ]
      (Master Catalog)             (Operational State)            (Parquet Data Lake)
             ▲                             ▲                             ▲
             └─────────────────────────────┴─────────────────────────────┘
                                           │
                                    [ Trino Engine ]
                              (Federated SQL Analytics)
```

---

## 2. Polyglot Persistence Strategy

The system distributes telemetry and master network catalog data across three distinct storage engines based on access patterns, query performance, and data lifecycle requirements:

| Storage Engine | Type | Data Schema / Format | Primary Responsibility | Query Engine |
| :--- | :--- | :--- | :--- | :--- |
| **PostgreSQL** | Relational (ACID) | Normalized SQL tables (`antennas`, `cells`, `locations`) | Master Data & Network Catalog (capacity limits, physical coordinates, hardware metadata). | Spring Data JPA / Direct SQL |
| **MongoDB** | Document NoSQL | Semi-structured BSON documents (`antenna_events`, `antenna_status`) | Hot/Warm operational state, fast read access for status dashboards, cell traffic aggregations. | Spring Data Mongo |
| **MinIO / S3** | Object Store | Immutable Parquet files (`s3://telemetry-lake/raw-events/`) | Cold storage / Data Lake for historical telemetry retention (> 1 year). | **Trino** (via Hive/Iceberg connector) |

---

## 3. Event Bus Specification (Apache Kafka - KRaft Mode)

* **Cluster Topology:** KRaft (Kafka Raft Metadata Mode, ZooKeeper-less, single-node container for local development & testing).
* **Primary Topic:** `telemetry.antenna.raw`
* **Retention Policy:** 14 days (`log.retention.hours=336`, `log.retention.bytes=-1`).
* **Partitioning Strategy:** Partitioned by `cell_id` or `antenna_id` using MurmurHash2 algorithm. Guarantees strict chronological ordering of telemetry events per physical cell while enabling horizontal scalability across consumer workers.
* **Consumer Group Architecture:**
  1. **`cg-telemetry-ingestion`**: Ingests raw events, validates domain constraints, updates MongoDB operational state (`antenna_status`), and batches records for Data Lake persistence.
  2. **`cg-telemetry-monitoring`**: Computes real-time sliding window metrics and emits network saturation alerts (`capacityPercentage > 80%`).
  3. **`cg-telemetry-audit`**: Independent audit and historical replay consumer attached to analytics/ELK pipelines with `auto.offset.reset=earliest` without impacting production ingestion throughput.

---

## 4. Hexagonal Component Mapping

### Inbound Adaptation
* `KafkaTelemetryConsumerAdapter`: Listens to `telemetry.antenna.raw` Kafka topic via `@KafkaListener`, deserializes JSON payloads, maps payloads into `ProcessTelemetryCommand`, and invokes the domain port `ProcessTelemetryUseCase`.
* `RestTelemetryController`: Provides REST HTTP endpoints (`POST /api/v1/telemetry`) mapping payloads into the exact same `ProcessTelemetryCommand`.

### Outbound Adaptation
* `KafkaTelemetryPublisherAdapter`: Implements `TelemetryEventPublisher` to emit domain events to Kafka topics asynchronously.
* `PostgresAntennaAdapter`: Implements `PostgresCatalogPort` to fetch antenna catalog dimensions and static capacity metadata.
* `MongoAntennaEventAdapter`: Implements `MongoTelemetryPort` to persist operational event records into MongoDB with optimistic locking (`@Version`) and unique index deduplication (`event_id`).
* `S3ParquetDataAdapter`: Implements `DataLakeStoragePort` to micro-batch events into columnar Parquet files pushed to MinIO/S3 object storage.

---

## 5. Architectural Decision Records (ADRs)

For architectural rationale and trade-off analysis, refer to:
* [ADR 0001: Record Architecture Decisions](file:///c:/dev/workspace/data_engineer/data-platform-sandbox/docs/adr/0001-record-architecture-decisions.md)
* [ADR 0002: Decoupled Ingestion Pipeline Architecture](file:///c:/dev/workspace/data_engineer/data-platform-sandbox/docs/adr/0002-decoupled-ingestion-pipeline.md)
* [ADR 0003: Hexagonal Architecture, DDD & SOLID Principles](file:///c:/dev/workspace/data_engineer/data-platform-sandbox/docs/adr/0003-hexagonal-architecture-ddd-solid.md)
* [ADR 0004: Hybrid Docker Compose & Testcontainers Sandbox BDD Strategy](file:///c:/dev/workspace/data_engineer/data-platform-sandbox/docs/adr/0004-docker-compose-testcontainers-bdd.md)
* [ADR 0005: Apache Kafka KRaft Event Bus & MinIO Parquet Data Lake Integration](file:///c:/dev/workspace/data_engineer/data-platform-sandbox/docs/adr/0005-event-driven-kafka-kraft-datalake.md)


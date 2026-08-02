# 5. Apache Kafka (KRaft Mode) & MinIO Parquet Data Lake Integration

Date: 2026-08-02

## Status

Accepted

## Context

As the Telecom Data Platform evolves, the initial queue architecture (SQS FIFO) presents architectural limitations for advanced enterprise telemetry operations:
1. **Single-Consumer Queue Limitations**: SQS queues are point-to-point buffers; fanout to multiple decoupled consumers (e.g., real-time congestion monitoring, operational DB ingestion, historical auditing) requires setting up separate SNS/SQS topic subscriptions.
2. **Lack of Log Replay**: Once an SQS message is consumed and deleted, it cannot be re-read by new or analytical consumer groups.
3. **Data Retention & Storage Costs**: Retaining high-frequency telemetry events in MongoDB or relational operational stores beyond 14 days causes index bloat and high storage costs.

We need an event-driven messaging layer that supports **multi-consumer pub-sub fanout**, **strict per-cell ordering**, **log replay capability**, and **low-cost cold storage Data Lake archiving** accessible via Trino.

---

## Decision

We will evolve the event-driven architecture by integrating **Apache Kafka in KRaft mode (ZooKeeper-less)** as the primary messaging bus and **MinIO / S3 Parquet** as the Data Lake tier:

### 1. ZooKeeper-less Apache Kafka (KRaft Mode)
* We deploy Kafka using **KRaft metadata mode** (`apache/kafka:3.7.0`). This eliminates the ZooKeeper ensemble, replaces double-state synchronization with an internal `@metadata` log topic, and reduces container footprint in local development.

### 2. Key-Based Routing Strategy (`cell_id` / `antenna_id`)
* Telemetry records are published with `cell_id` as the partition key. MurmurHash2 hashing guarantees that all events from the same antenna cell land on the exact same Kafka partition, maintaining **strict FIFO timestamp ordering per cell** while allowing parallel consumption across partitions.

### 3. Multi-Consumer Group Fanout
* **`cg-telemetry-ingestion`**: Consumes `telemetry.antenna.raw` and persists operational facts to MongoDB with optimistic locking (`@Version`) and unique index deduplication (`eventId`).
* **`cg-telemetry-monitoring`**: Computes sliding window capacity and emits real-time alerts to `telemetry.antenna.alerts` when `capacityPercentage > 80%`.
* **`cg-telemetry-audit`**: Configured with `auto.offset.reset = earliest` to stream historical events from offset 0 into audit/logging pipelines without mutating production database state.

### 4. MinIO / S3 Parquet Data Lake & Trino Integration
* Events are micro-batched into compressed, columnar **Parquet files** written to MinIO (`s3://telemetry-lake/raw-events/`).
* Trino queries cold Data Lake Parquet files via a dedicated catalog configuration (`datalake.properties`) using the Hive/Iceberg connector, enabling federated SQL joins across PostgreSQL static catalog, MongoDB operational state, and MinIO Parquet files.

### 5. Resilience & Dead Letter Topic (DLT)
* We use Spring Kafka's `ErrorHandlingDeserializer` to route malformed payloads directly to `telemetry.antenna.raw.DLT`, preventing poison pill messages from causing infinite consumer loop failures.

---

## Consequences

* **Pros:**
  - **Multi-Consumer Fanout & Replay**: Decoupled consumer groups can independently read, process, or replay historical events from offset 0 without side effects.
  - **Per-Cell Ordering with High Throughput**: Partitioning by `cell_id` guarantees order per cell while enabling parallel scaling across multiple partition workers.
  - **Cost-Effective Long-Term Retention**: Micro-batching raw telemetry into immutable Parquet files on MinIO/S3 reduces database storage overhead and enables fast columnar queries in Trino.
  - **Reduced Operational Complexity**: KRaft mode removes ZooKeeper, simplifying container deployment and eliminating metadata failover latency.

* **Cons:**
  - **Partition Key Management**: Care must be taken when selecting partition keys to avoid hotspotting single partitions (skewed traffic).
  - **Eventual Consistency**: Consumer groups operate asynchronously, requiring application-level idempotency checks in MongoDB (`eventId` unique index) to handle retries cleanly.

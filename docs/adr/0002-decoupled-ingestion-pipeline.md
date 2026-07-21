# 2. Decoupled Ingestion Pipeline with SQS FIFO

Date: 2026-07-21

## Status

Accepted

## Context

The platform requires the ingestion of high-throughput telemetry events (voice calls and data sessions) at rates reaching 50,000 events/second during traffic peaks. 
To avoid overloading operational databases, we need a buffer/queue mechanism to absorb traffic spikes.
Additionally, we must guarantee exactly-once processing (or at-least-once delivery with application-level idempotency checks) and maintain message sequencing.

## Decision

We will use an **AWS SQS FIFO** queue (or a local stack simulation in the development environment) to decouple telemetry event producers from consumers.
- **Why SQS FIFO over Kafka:** SQS FIFO is preferred for this architecture because of its lower operational overhead, 1-to-1 processing simplicity, and native message deduplication (based on message deduplication ID).
- **Idempotency:** While SQS FIFO guarantees at-least-once delivery (and exactly-once within a 5-minute deduplication window), the application will enforce idempotency by using a unique event identifier hash (`id_evento`) and checking for its existence in MongoDB before insertion.
- **Data Partitioning:**
  - PostgreSQL will store the static infrastructure dimension `antennas`.
  - MongoDB will store high-frequency, append-only `antenna_events`.
  - Trino will bridge the two data catalogs for federated SQL querying.

## Consequences

- **Scalability:** The ingestion layer can absorb massive traffic peaks without impacting query latency or database CPU utilization.
- **Idempotency Overhead:** Every ingested event requires a read check or upsert constraint in MongoDB to ensure idempotency. Optimistic locking will be handled using Spring Data MongoDB's `@Version` to handle concurrency conflicts.
- **Complexity:** Requires running SQS/LocalStack, MongoDB, PostgreSQL, and Trino in the local development environment using Docker Compose.

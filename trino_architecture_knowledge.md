# Trino Custom UDFs & Distributed Query Engine: Architecture & Ingestion Guide

This document is a comprehensive guide to distributed SQL engines, covering Coordinator/Worker topology, Predicate Pushdown, NoSQL/Relational data federation, physical/logical data structures, memory performance, concurrency controls, and custom Trino UDF plugin development.

It links theoretical concepts directly to the implementation in this sandbox project.

---

## 1. Core Topology: Coordinator vs. Workers

Trino does not store data itself. It is a Massively Parallel Processing (MPP) query engine that delegates storage to external systems and performs execution in memory across a cluster.

```text
               ┌─────────────────────────┐
               │    Client (e.g. SQL)    │
               └────────────┬────────────┘
                            │ SQL Query
                            ▼
               ┌─────────────────────────┐
               │       COORDINATOR       │  <-- The Brain: Parses, plans,
               └──────┬───────────┬──────┘      optimizes, and schedules splits.
                      │ Task      │ Task
                      ▼           ▼
         ┌────────────────┐   ┌────────────────┐
         │     WORKER     │   │     WORKER     │  <-- The Muscle: Fetches data via
         └───────┬────────┘   └───────┬────────┘      connectors, executes filters,
                 │                    │               joins, UDFs, and shuffles data.
                 ▼                    ▼
           [ PostgreSQL ]        [ MongoDB ]
```

### 1.1. The Coordinator (The Brain)
*   **Parsing & Analysis**: Receives SQL queries, checks syntax, resolves catalog schemas, and builds the logical syntax tree.
*   **Cost-Based Optimization (CBO)**: Rewrites queries to choose the fastest execution paths based on catalog metadata statistics (row counts, table sizes, join reordering).
*   **Scheduling**: Splits the query execution plan into atomic tasks called **Splits** and schedules them to run in parallel across the Workers.

### 1.2. The Workers (The Muscle)
*   **Data Ingestion**: Use connector libraries to read raw splits of data from the source systems.
*   **Data Processing**: Run execution pipelines in memory (Projecting columns, filtering rows, running aggregation accumulators, executing custom Java/C++ UDFs).
*   **Data Shuffling (Network Exchanges)**: When performing operations like `GROUP BY location` or `JOIN`, workers partition records by hashing the keys and transferring them over the network so that all rows with the same key end up on the same worker for final calculation.

> [!NOTE]
> In our sandbox deployment, the Trino coordinator and worker run within the same container to optimize local resource usage.
> Reference: [docker-compose.yml](./docker-compose.yml#L51-L65)

---

## 2. Predicate Pushdown (Minimizing Network I/O)

In a federated environment, the network is the ultimate bottleneck. Trino uses **Predicate Pushdown** to optimize data retrieval:

*   **Definition**: The Coordinator evaluates the SQL `WHERE` clause (e.g. `WHERE antenna_id = 'MAD-01'`) and pushes this filter directly into the query issued to the connector source.
*   **Without Pushdown**: Trino would have to request all 100 million records from MongoDB, transfer them over the network, and discard 99.9% of them in memory.
*   **With Pushdown**: Trino requests only the records matching the filter. The source database uses its own indexes, filters the rows locally, and transfers only the small subset of results over the network.

---

## 3. Connectors & Catalogs (Abstracting Heterogeneous Systems)

Trino abstracts data sources into a three-level hierarchy: **`Catalog.Schema.Table`**

*   **Catalog**: Represents a specific connector configuration (e.g., `postgresql` or `mongodb`).
    *   PostgreSQL Catalog Configuration: [postgresql.properties](./src/main/resources/docker/trino/catalog/postgresql.properties)
    *   MongoDB Catalog Configuration: [mongodb.properties](./src/main/resources/docker/trino/catalog/mongodb.properties)
*   **Schema**: Maps to a database/namespace in the source (e.g., `public` or `telemetry`).
*   **Table**: Represents a tabular schema. For NoSQL sources like MongoDB, the connector dynamically maps BSON document fields to relational column types.

Internally, Trino converts all source-specific formats into a standardized, in-memory columnar format called **Trino Pages**. Once converted into Pages, Trino can join Postgres tables and MongoDB collections seamlessly in RAM.

---

## 4. Physical Partitioning vs. Logical Indexing

Understanding how data layout in source databases affects parallel execution in Trino is critical:

| Concept | Structure Type | Purpose in Distributed Execution |
| :--- | :--- | :--- |
| **Physical Partitioning** | **Physical Layout** | Decides how data files/tables are split on disk. Determines how many independent **Splits** Trino can schedule in parallel. |
| **Logical Indexing** | **Logical Lookup Index** | A search helper index (like B-Tree or Hash). Enables fast row retrieval *inside* a specific physical table/partition, avoiding a full scan. |

### 4.1. Combined Execution Scenario
Suppose a table is physically partitioned by **Month** (12 monthly tables), and has a local index on **City**:

```sql
SELECT * FROM telemetria WHERE ciudad = 'Madrid';
```

Because there is no filter on `month`, Trino must query all 12 partitions to find the results:
1.  **Parallel Split Generation**: Trino creates **12 Splits** (one for each monthly partition) and distributes them across the Workers.
2.  **Indexed Scanning per Worker**: When Worker 1 queries the January partition, the underlying database uses the **local index on City** to read only the rows matching `'Madrid'`. The same happens on the other 11 partitions in parallel.
3.  **Result**: You get the best of both worlds: parallel processing across 12 partitions and fast indexed data access inside each partition.

### 4.2. Partition Pruning (Poda de Particiones)
If the query filters on the partition key:
```sql
SELECT * FROM telemetria WHERE mes = 'Enero' AND ciudad = 'Madrid';
```
Trino performs **Partition Pruning**. It completely discards the other 11 month partitions before reading. It generates only **1 Split** (for January), and a single worker executes the query using the city index. This uses fewer cluster resources because it prunes unneeded data early.

---

## 5. Data Modeling for Massive Ingestion: Append-Only Patterns

In high-concurrency ingestion systems (such as IoT telemetry, clickstreams, or event sourcing), standard database design must be optimized to prevent locks and write bottlenecks.

### 5.1. Fact vs. Dimension Tables in IoT/Telemetry
Data is split into two logical tables to optimize storage and transaction models:

*   **Dimension Tables (e.g. `postgresql.public.antennas`)**:
    *   **Content**: Reference data, configuration, attributes of the telemetry nodes (e.g. location, capacity).
    *   **Update Frequency**: Low/Slow (Slowly Changing Dimensions).
    *   **Consistency Model**: Strong Consistency (ACID) to ensure exact metadata configuration.
    *   Reference Implementation: [AntennaJpaEntity.java](./src/main/java/com/telecom/analytics/platform/infrastructure/adapter/outbound/persistence/postgres/AntennaJpaEntity.java)
*   **Fact/Event Tables (e.g. `mongodb.telemetry.antenna_events`)**:
    *   **Content**: Continuous measurements, metrics, transactions (e.g. session bytes, duration).
    *   **Update Frequency**: High volume, non-stop stream.
    *   **Consistency Model**: Eventual Consistency (BASE) to prioritize write availability and horizontal scale.
    *   Reference Implementation: [AntennaEventMongoDocument.java](./src/main/java/com/telecom/analytics/platform/infrastructure/adapter/outbound/persistence/mongodb/AntennaEventMongoDocument.java)

### 5.2. Immutable (Append-Only) Writes vs. In-Place Updates
Under high concurrency, updating records (e.g., executing `UPDATE antennas SET total_bytes = total_bytes + X`) creates major performance bottlenecks:

*   **Thread Contention & Row Locking**: An `UPDATE` requires taking exclusive row locks or document locks in database storage. If thousands of requests try to update the same row simultaneously, they block each other, causing write latency, lock queues, and deadlocks.
*   **Append-Only (`INSERT`) Solution**: By treating all incoming events as immutable facts, we only perform `INSERT` operations. Inserts do not lock existing rows; they just write data to the end of the table or partition. This eliminates thread contention and allows write scaling.
*   **Historical Trace**: Append-only modeling preserves the full history of changes (time-series), enabling historical analytics (e.g. window functions) that would be impossible if values were updated in-place.

### 5.3. Strong Consistency (ACID) vs. Eventual Consistency (BASE)

| Metric | Strong Consistency (ACID) | Eventual Consistency (BASE) |
| :--- | :--- | :--- |
| **Primary Goal** | Instantaneous data correctness and isolation across all reads. | High availability, speed, and partitioning fault-tolerance. |
| **Mechanism** | Write locks, transaction logs, and rigid schema constraint validation. | Optimistic locks, memory-first writes, and asynchronous replica synchronization. |
| **Trade-off** | Limits write concurrency; susceptible to performance degradation under massive IoT streams. | A query reading from a secondary node might see slightly stale data (a few milliseconds delay) before all replicas catch up. |
| **Application** | Reference metadata inventory in PostgreSQL. | High-throughput telemetry event ingestion in MongoDB. |

---

## 6. JVM Memory Management & Performance in Big Data Engines

Big Data query engines written in Java face unique challenges due to the way the Java Virtual Machine (JVM) manages memory.

### 6.1. The Impact of Garbage Collector Stop-the-World (STW) Pauses
Standard Java objects are allocated inside the **JVM Heap**. In large-scale clusters processing billions of rows:
*   **Object Overhead**: Creating Java object wrappers (e.g. `Double`, `Long`, `Record`) for millions of rows quickly consumes gigabytes of heap memory.
*   **Stop-the-World (STW) Pauses**: When the Garbage Collector runs a major sweep to clean up dead objects, it must freeze all application processing threads to safely inspect memory.
*   **The GC Death Spiral**: For large JVM heaps (e.g., 64GB - 256GB), an STW pause can last from seconds to minutes. During this freeze, the Worker stops responding to heartbeat pings from the Coordinator, causing the Coordinator to drop the Worker from the active cluster.

### 6.2. Off-Heap Memory Management
To bypass Garbage Collection overhead, modern engines allocate and manage query buffers (used for Joins, Shuffles, and Sorting) outside the standard JVM Heap:
*   **Mechanism**: The engine allocates raw bytes directly in the operating system's native memory (using direct ByteBuffers or Java 22 Project Panama `Arena` segments).
*   **GC Exemption**: The JVM Garbage Collector **does not scan** off-heap memory. 
*   **Manual Allocation Lifecycle**: The engine takes responsibility for manually allocating and deallocating this memory, preventing memory leaks while keeping the JVM Heap footprint small and GC pauses under milliseconds.

### 6.3. Project Panama FFM API Native Offloading
By combining **Off-Heap Memory** with **Project Panama FFM API** downcall handles, Java engines can offload heavy mathematical calculations directly to pre-compiled native binaries (C++ / Rust):

1.  **Zero-Copy Memory Mapping**: Instead of copying data from the OS buffer to Java heap objects, a direct pointer to the Off-Heap `MemorySegment` is passed to the C++ shared library.
    *   Reference C++ Native Implementation: [native_analytics.cpp](./src/main/cpp/native_analytics.cpp)
2.  **Hardware Acceleration**: The C++ library processes the raw memory segment using compiler-level optimizations (such as AVX/SIMD vector instruction registers) at hardware speed.
3.  **Zero-Heap Overhead**: Since no Java objects are created during the massive calculations, GC overhead remains zero, bypassing the standard JVM performance penalty.
    *   Reference Panama FFM Adapter Implementation: [NativeAnalyticsAdapter.java](./src/main/java/com/telecom/analytics/platform/infrastructure/adapter/outbound/nativeopt/NativeAnalyticsAdapter.java)

---

## 7. Distributed Concurrency, Message Broker Delivery, & Idempotency

In distributed systems, network partitions and component failures are normal occurrences. Software must be designed with strict transaction controls to handle concurrent modifications and duplicate messages.

### 7.1. Message Broker Delivery Models & Idempotency
Message queues (such as AWS SQS or Apache Kafka) generally guarantee **At-Least-Once Delivery**:
*   **The Duplicate Problem**: A worker receives a message, processes it successfully, but the network fails before the worker can acknowledge (delete) the message from the queue. The queue will re-deliver the message to another worker, resulting in a duplicate.
*   **Application-Level Idempotency**: An operation is idempotent if running it multiple times produces the same system state as running it once.
    *   **Implementation**: Assign a unique `eventId` (UUID) at the message producer level.
    *   **Database Constraints**: In MongoDB/PostgreSQL, create a **Unique Index** on the `eventId` field. If a duplicate message is received and we attempt to insert it, the database rejects it with a duplicate key exception. The application handles the exception safely, acknowledges the queue, and prevents duplicate data corruption.
    *   Reference SQS Telemetry Listener Implementation: [TelemetryIngestionService.java](./src/main/java/com/telecom/analytics/platform/application/service/TelemetryIngestionService.java)

### 7.2. Optimistic vs. Pessimistic Locking
When multiple threads attempt to update the same record (e.g. changing an active antenna's location or capacity status concurrently):

*   **Pessimistic Locking**: Takes an exclusive write lock on the database row at read time (e.g. `SELECT FOR UPDATE`). No other transaction can read or write that row until the locking transaction finishes. Causes severe thread blocking (contention), high latency, and deadlocks under scale.
*   **Optimistic Locking (`@Version`)**: Does not lock the row. Instead, the entity has a `version` column (integer).
    *   If a thread attempts to write an entity whose version has already been incremented on disk by another transaction, the database returns 0 updated rows, and Hibernate throws an `OptimisticLockException`.
    *   Reference JPA Entity `@Version` field: [AntennaJpaEntity.java](./src/main/java/com/telecom/analytics/platform/infrastructure/adapter/outbound/persistence/postgres/AntennaJpaEntity.java#L28)

---

## 8. Trino UDF Plugin Development & Docker Deployment

To extend Trino SQL syntax with native-offloaded logic or custom business aggregations, we implement a custom UDF plugin.

### 8.1. Plugin Architecture & Discovery
Trino executes custom logic at runtime through a Plugin system loaded via Java's **`ServiceLoader`** API:
1.  **Service Provider File**: Must reside in the jar resources at [io.trino.spi.Plugin](./trino-custom-analytics-plugin/src/main/resources/META-INF/services/io.trino.spi.Plugin). It contains the fully qualified name of the class implementing `io.trino.spi.Plugin`.
2.  **Plugin Entry Point**: Implements the `Plugin` interface and overrides `getFunctions()` to list the custom scalar, aggregation, and window function classes.
    *   Reference Implementation: [CustomAnalyticsPlugin.java](./trino-custom-analytics-plugin/src/main/java/com/telecom/trino/plugin/CustomAnalyticsPlugin.java)
3.  **Maven Compilation Packaging**: Configure `maven-dependency-plugin` inside [pom.xml](./trino-custom-analytics-plugin/pom.xml) to copy all runtime dependencies (such as Guava) alongside the main plugin JAR into a target directory `target/custom-analytics/`.

### 8.2. UDF Types Implementation

#### A. Scalar UDF (`calculate_stddev_native`)
Exposes a standard deviation calculation over arrays of longs (bigint) using the Project Panama C++ native library downcall or a Java fallback calculation.
*   Reference Implementation: [StdDevNativeFunction.java](./trino-custom-analytics-plugin/src/main/java/com/telecom/trino/plugin/scalar/StdDevNativeFunction.java)

#### B. Aggregation UDF / UDAF (`avg_duration_native`)
Calculates average values over a streaming set of rows, managing an accumulator state object dynamically.
*   Reference State Interface: [AvgState.java](./trino-custom-analytics-plugin/src/main/java/com/telecom/trino/plugin/aggregate/state/AvgState.java)
*   Reference Aggregation Logic: [AvgDurationNativeAggregation.java](./trino-custom-analytics-plugin/src/main/java/com/telecom/trino/plugin/aggregate/AvgDurationNativeAggregation.java)

#### C. Window UDF (`native_running_sum`)
Computes a cumulative sum of values across rows in a partition using a stateful sequential approach.
*   Reference Implementation: [NativeRunningSumWindowFunction.java](./trino-custom-analytics-plugin/src/main/java/com/telecom/trino/plugin/window/NativeRunningSumWindowFunction.java)

### 8.3. Docker Compose Volume Deployment
The compiled plugin directory `target/custom-analytics` is mounted directly into the Trino container's plugin directory:
```yaml
volumes:
  - ./trino-custom-analytics-plugin/target/custom-analytics:/usr/lib/trino/plugin/custom-analytics:ro
```
*   Reference volume mount configuration: [docker-compose.yml](./docker-compose.yml#L64)

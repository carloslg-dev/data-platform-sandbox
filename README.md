# Data Platform Sandbox — Telecom Data Platform & Telemetry Architecture

[![Java 22](https://img.shields.io/badge/Java-22-orange.svg)](https://openjdk.org/projects/jdk/22/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.3.x-green.svg)](https://spring.io/projects/spring-boot)
[![Trino](https://img.shields.io/badge/Trino-451-blue.svg)](https://trino.io/)

## 🎯 Executive Summary
**`data-platform-sandbox`** is a containerized analytics sandbox simulating a high-throughput mobile network telemetry and Customer Experience Management (CEM) platform. 

It demonstrates distributed SQL querying, federated data processing across heterogeneous data stores, and immutable append-only event modeling, serving as a reference architecture for Senior Backend / Data Engineer technical interviews.

---

## 🏛️ Deployment & Architecture Diagram

Below is the layout of the deployed modules, databases, message brokers, and execution ports in the sandbox environment:

```text
                                  ┌───────────────────────────────┐
                                  │      DBeaver / CLI Client     │
                                  └───────────────┬───────────────┘
                                                  │ SQL Queries (Port 8080)
                                                  ▼
   ┌──────────────────────────────────────────────────────────────────────────────┐
   │                               TRINO ENGINE                                   │
   │  ┌────────────────────────────────────────────────────────────────────────┐  │
   │  │                       Trino Coordinator (Port 8080)                    │  │
   │  └───────────────────────────────────┬────────────────────────────────────┘  │
   │                                      │ Task Delegation / Page Streams        │
   │  ┌───────────────────────────────────▼────────────────────────────────────┐  │
   │  │                          Trino Workers (Port 8080)                     │  │
   │  │   [Custom UDFs: Scalar, Aggregations, Window Functions (Java/C++ FFM)] │  │
   │  └───────────────┬───────────────────────────────────────────────┬────────┘  │
   └──────────────────┼───────────────────────────────────────────────┼───────────┘
                      │ JDBC Queries (Port 5432)                      │ BSON Protocol (Port 27017)
                      ▼                                               ▼
     ┌──────────────────────────────────┐            ┌──────────────────────────────────┐
     │          POSTGRESQL DB           │            │            MONGODB               │
     │      (Ports: 5432 -> 5432)       │            │     (Ports: 27017 -> 27017)      │
     │   - Dimension data (antennas)    │            │   - Fact data (antenna_events)   │
     └────────────────▲─────────────────┘            └────────────────▲─────────────────┘
                      │ Writes (Port 5432)                            │ Inserts (Port 27017)
                      │                                               │
   ┌──────────────────┴───────────────────────────────────────────────┴───────────┐
   │                          SPRING BOOT INGESTION APP                           │
   │                                 (Port 8081)                                  │
   │  ┌────────────────────────────────────────────────────────────────────────┐  │
   │  │                    SQS Telemetry Listener Container                    │  │
   │  └──────────────────────────────────▲─────────────────────────────────────┘  │
   │                                     │ Long Poll Messages (Port 4566)         │
   │  ┌──────────────────────────────────┴─────────────────────────────────────┐  │
   │  │                      TelemetrySimulationRunner                         │  │
   │  │            (Streams 1200 messages/min to SQS under "simulate")         │  │
   │  └──────────────────────────────────┬─────────────────────────────────────┘  │
   └─────────────────────────────────────┼────────────────────────────────────────┘
                                         │ SQS Enqueue (Port 4566)
                                         ▼
                        ┌──────────────────────────────────┐
                        │      LOCALSTACK (AWS SQS)        │
                        │      (Ports: 4566 -> 4566)       │
                        │      - Queue: telemetry-queue    │
                        └──────────────────────────────────┘
```

---

## 📂 Project Directory Structure

The project is structured following **Hexagonal Architecture (Ports and Adapters)** and **Domain-Driven Design (DDD)** principles to separate pure business logic from infrastructural details:

```text
data-platform-sandbox/
├── docs/                                  # Project documentation (ADRs, Case study, etc.)
│   ├── adr/                               # Architecture Decision Records
│   │   ├── 0001-record-architecture-decisions.md
│   │   ├── 0002-decoupled-ingestion-pipeline.md
│   │   ├── 0003-hexagonal-architecture-ddd-solid.md
│   │   └── 0004-docker-compose-testcontainers-bdd.md
│   ├── specs/                             # Domain specs & Terminology
│   └── case_study.md                     # Case study specifications
├── trino-custom-analytics-plugin/        # Custom Trino UDF plugin subproject (Java 22)
├── src/
│   ├── main/
│   │   ├── cpp/                          # C++ Native Optimization library (JNI/Panama)
│   │   ├── java/
│   │   │   └── com/telecom/analytics/platform/
│   │   │       ├── domain/               # Pure Domain Model & Business Logic (no framework dependencies)
│   │   │       │   ├── model/            # Entities, Value Objects, Aggregates
│   │   │       │   └── repository/       # Outbound Ports (Repository Interfaces)
│   │   │       ├── application/          # Application Use Cases & Core Services
│   │   │       │   ├── port/             # Inbound & Outbound Ports (Use Cases & Queue Listeners)
│   │   │       │   └── service/          # Use Case Implementations (Application Services)
│   │   │       ├── infrastructure/       # Framework-specific adapters & configurations
│   │   │       │   ├── adapter/          # Inbound (Listeners, REST) & Outbound (Postgres JPA, Mongo DB)
│   │   │       │   └── config/           # Spring configuration & Bean definitions
│   │   │       └── PlatformApplication.java
│   │   └── resources/
│   │       ├── application.yml           # Spring Boot configurations (server port 8081)
│   │       └── docker/                   # Trino catalog properties & configs
│   └── test/
│       ├── java/                         # Step definitions, Unit and Integration Tests
│       └── resources/
│           └── features/                 # Gherkin BDD Feature Files
│               ├── concurrency_resilience.feature
│               ├── cross_catalog_analytics.feature
│               ├── event_ingestion.feature
│               └── native_optimization.feature
├── trino_architecture_knowledge.md       # Core Distributed System & Ingestion Study Guide
└── README.md                             # This file
```

---

## 🚀 1. Setup & Getting Started

Follow these steps to build the custom analytics plugin, boot the infrastructure containers, and run the ingestion and simulation apps.

### Step 1.1: Build & Package the Trino Custom Plugin
Compile the custom UDF plugin sub-project. This generates the plugin classes and automatically packages all runtime dependencies (such as Guava) into the target directory `target/custom-analytics` ready for Docker volume mounting:
```powershell
cd trino-custom-analytics-plugin
mvn clean package
cd ..
```

### Step 1.2: Spin up the Infrastructure Containers (Docker Compose)
Launch the containerized sandbox environment containing PostgreSQL, MongoDB, LocalStack SQS, and Trino:
```powershell
docker compose up -d
```
Verify that all containers are healthy:
```powershell
docker compose ps
```

### Step 1.3: Run the Main Spring Boot Application
Starts the core ingestion listener service on port `8081` (avoiding port conflicts with Trino on `8080`):
```powershell
mvn spring-boot:run
```

### Step 1.4: Run the Telemetry Live Load Simulation
To simulate a continuous stream of live traffic from antennas (generating and processing ~1200 events/minute via SQS and persisting them to MongoDB), run the application activating the `simulate` profile:
```powershell
mvn spring-boot:run "-Dspring-boot.run.profiles=simulate"
```

---

## 📊 2. Querying Federated Data from Trino

Trino exposes its query interface on `http://localhost:8080`. You can query it via the Trino interactive CLI inside the container or by connecting any graphic SQL client like DBeaver (Host: `localhost`, Port: `8080`, User: `admin`, Password: empty).

### Accessing the Trino CLI Terminal
```powershell
docker compose exec trino trino --server localhost:8080
```

### 2.1. Classic Cross-Catalog Join: Most Active Antennas
Find which physical antennas (stored in Postgres) are processing the highest volume of telemetry logs (stored in MongoDB):
```sql
SELECT 
    a.id AS antenna_id,
    a.location,
    a.type AS technology,
    COUNT(e.event_id) AS total_events
FROM postgresql.public.antennas a
JOIN mongodb.telemetry.antenna_events e ON a.id = e.antenna_id
GROUP BY a.id, a.location, a.type
ORDER BY total_events DESC;
```

### 2.2. Over-Utilized Antennas (> 80% Capacity)
Find antennas operating close to their theoretical capacity limit by comparing aggregated session traffic against relational dimension thresholds:
```sql
SELECT 
    a.id AS antenna_id,
    a.location,
    a.theoretical_capacity AS capacity_mbps,
    SUM(e.bytes_transferred) / (1024 * 1024) AS total_traffic_mb,
    (SUM(e.bytes_transferred) / (1024.0 * 1024.0)) / a.theoretical_capacity * 100 AS capacity_utilization_pct
FROM postgresql.public.antennas a
JOIN mongodb.telemetry.antenna_events e ON a.id = e.antenna_id
GROUP BY a.id, a.location, a.theoretical_capacity
HAVING (SUM(e.bytes_transferred) / (1024.0 * 1024.0)) > (a.theoretical_capacity * 0.8)
ORDER BY capacity_utilization_pct DESC;
```

### 2.3. Querying Custom UDFs (Scalar, Aggregation & Window UDFs)
Test all three categories of our custom plugin's functions in a single advanced analytical query over the MongoDB event dataset:
```sql
SELECT 
    antenna_id,
    duration_ms,
    classify_antenna_utilization(bytes_transferred, 100) as load_status,
    avg_duration_native(duration_ms) OVER (PARTITION BY antenna_id) as native_avg_duration,
    native_running_sum(bytes_transferred) OVER (PARTITION BY antenna_id ORDER BY timestamp) as running_bytes,
    native_lag(duration_ms, 1) OVER (PARTITION BY antenna_id ORDER BY timestamp) as prev_duration
FROM mongodb.telemetry.antenna_events
LIMIT 10;
```
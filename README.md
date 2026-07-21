# Data Platform Sandbox — Telecom Data Platform & Telemetry Architecture

[![Java 21](https://img.shields.io/badge/Java-21-orange.svg)](https://openjdk.org/projects/jdk/21/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.x-green.svg)](https://spring.io/projects/spring-boot)
[![Trino](https://img.shields.io/badge/Trino-Distributed%20SQL-blue.svg)](https://trino.io/)

## 🎯 Executive Summary
**`data-platform-sandbox`** is a containerized architecture and analytics sandbox simulating a high-throughput mobile network analytics and Customer Experience Management (CEM) platform. 

It demonstrates distributed SQL querying, federated data processing across heterogeneous data stores, and immutable append-only event modeling, serving as a reference architecture for Senior Backend / Solution Architect technical interviews.

---

## 🏛️ Architecture & Data Model

The platform separates static infrastructural dimensions from high-frequency, immutable network event telemetry to eliminate write-contention and lock overhead.

* **Relational Storage (PostgreSQL):** Stores static master/dimension data (e.g., `antennas` table containing geographical locations, maximum theoretical capacity, and operational status).
* **Document Storage (MongoDB):** Stores high-throughput, append-only immutable event streams (e.g., `antenna_events` collection tracking data sessions and voice calls).
* **Distributed Query Engine (Trino):** Bridges both data sources via custom connector catalogs, enabling zero-ETL federated `JOIN` queries across relational and NoSQL engines in memory.

---

## 📂 Project Directory Structure

The project is structured following **Hexagonal Architecture (Ports and Adapters)** and **Domain-Driven Design (DDD)** principles to separate pure business logic from infrastructural details:

```text
data-platform-sandbox/
├── docs/                                  # Project documentation
│   ├── adr/                              # Architecture Decision Records
│   │   ├── 0001-record-architecture-decisions.md
│   │   ├── 0002-decoupled-ingestion-pipeline.md
│   │   ├── 0003-hexagonal-architecture-ddd-solid.md
│   │   └── 0004-docker-compose-testcontainers-bdd.md
│   ├── specs/                             # Specifications & Domain Terminology
│   │   └── terminology.md
│   └── case_study.md                     # Case study specifications
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── com/
│   │   │       └── telecom/
│   │   │           └── analytics/
│   │   │               └── platform/
│   │   │                   ├── domain/          # Pure Domain Model & Business Logic (no framework dependencies)
│   │   │                   │   ├── model/       # Entities, Value Objects, Aggregates, Invariants
│   │   │                   │   └── repository/  # Outbound Ports (Repository Interfaces)
│   │   │                   ├── application/     # Application Use Cases & Core Services
│   │   │                   │   ├── port/
│   │   │                   │   │   ├── inbound/ # Inbound Ports (Use Case Interfaces)
│   │   │                   │   │   └── outbound/# Outbound Ports (Queue/Notification interfaces)
│   │   │                   │   └── service/     # Use Case Implementations (Application Services)
│   │   │                   ├── infrastructure/  # Framework-specific adapters & configurations
│   │   │                   │   ├── adapter/
│   │   │                   │   │   ├── inbound/ # Queue Listeners, REST Controllers, CLI adapters
│   │   │                   │   │   └── outbound/# DB Implementations (PostgreSQL JPA, MongoDB repositories)
│   │   │                   │   └── config/      # Spring configuration, Trino connector setup, Bean definitions
│   │   │                   └── PlatformApplication.java
│   │   └── resources/
│   │       ├── application.yml
│   │       └── docker/                          # Compose environment (Trino, Postgres, MongoDB, LocalStack)
│   └── test/
│       ├── java/                                # Step definitions, Unit and Integration Tests
│       └── resources/
│           └── features/                        # Gherkin BDD Feature Files
│               ├── concurrency_resilience.feature
│               ├── cross_catalog_analytics.feature
│               ├── event_ingestion.feature
│               └── native_optimization.feature
├── AGENT.md                               # Rules and guidelines for AI coding agents
└── README.md                              # This file
```
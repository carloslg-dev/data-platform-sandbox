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
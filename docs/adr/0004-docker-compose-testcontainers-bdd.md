# 4. Local Sandbox, Integration Testing, and BDD (Docker, Testcontainers, Cucumber)

Date: 2026-07-21

## Status

Accepted

## Context

The data platform sandbox integrates multiple distributed systems: PostgreSQL (relational master data), MongoDB (NoSQL telemetry logs), AWS SQS FIFO (decoupled ingestion queue), and Trino (distributed federated SQL query engine). 

To ensure developer efficiency, reproducible environments, and continuous verification of business constraints without complex manual setups, we require:
1. A lightweight, unified local runtime environment.
2. An automated strategy for integration testing against real datastores without cross-test data pollution.
3. Spec-driven verification linking functional requirements directly to test assertions.

## Decision

We will adopt a containerized local sandbox approach combined with Testcontainers for integration testing and Cucumber/Gherkin for Behavior-Driven Development (BDD).

### 1. Unified Local Sandbox (Docker Compose)
We will maintain a `docker-compose.yml` configuration defining all infrastructural dependencies:
* **PostgreSQL:** For master infrastructure data (`antennas`).
* **MongoDB:** For high-throughput events.
* **LocalStack:** Simulating AWS SQS FIFO queue for the ingestion layer.
* **Trino:** Running federated queries. Connectors to PostgreSQL and MongoDB are mounted dynamically as catalog configurations.

Developers can spin up the entire sandbox locally with a single command (`docker compose up -d`).

### 2. Ephemeral Integration Testing (Testcontainers)
For automated integration testing (especially for repositories and pipeline consumers):
* We will use **Testcontainers Java** to dynamically spin up Docker containers for PostgreSQL, MongoDB, and SQS/LocalStack during the test phase.
* Tests will configure dynamic database credentials and ports injected into Spring Boot configuration (`@DynamicPropertySource`), preventing dependencies on any globally running developer infrastructure.
* Ephemeral containers guarantee a clean slate for every test execution, eliminating test flakiness caused by leftover database state.

### 3. Spec-Driven Validation (BDD with Cucumber)
* **Gherkin Feature Files:** All core scenarios (ingestion flow, idempotency, saturation computations, optimistic locking, federated queries) are documented using Gherkin syntax (`.feature` files) under `src/test/resources/features/`.
* **Cucumber Test Runner:** We will run these specs using the JUnit-based Cucumber runner, binding scenario steps to Java code using Spring Boot test contexts.
* Feature files serve as living documentation that validates the complete system integration from the inbound queue down to MongoDB and PostgreSQL.

## Consequences

* **Pros:**
  - **Zero Local Dependency Installation:** Only Docker is required on developer machines or CI/CD pipelines.
  - **Isolated Integration Tests:** Completely eliminates "it works on my machine" issues caused by local database state differences.
  - **Executable Specifications:** BDD ensures that the functional scope defined in specs is always verified by running code.
* **Cons:**
  - **Execution Latency:** Test execution times are longer due to the overhead of starting and stopping Docker containers during test suites.
  - **Resource Overhead:** Ephemeral containers demand higher CPU and memory resources during automated builds.

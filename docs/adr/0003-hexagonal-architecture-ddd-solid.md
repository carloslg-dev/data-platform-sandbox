# 3. Clean Architecture: Hexagonal Ports & Adapters, Domain-Driven Design (DDD), Clean Code, and SOLID

Date: 2026-07-21

## Status

Accepted

## Context

The Telecom Data Platform requires high performance, strict telemetry event processing, concurrency resilience (optimistic locking), and native optimization (C++/JNI memory sharing). 

To ensure the core business logic (invariants, analytics, and telemetry routing) remains maintainable, testable, and decoupled from framework constraints (Spring Boot) and database choices (PostgreSQL, MongoDB), we need a rigorous architecture pattern. The system must also align with Clean Code practices and SOLID principles.

## Decision

We will design and implement the codebase following **Hexagonal Architecture (Ports and Adapters)** coupled with **Domain-Driven Design (DDD)** tactical patterns, and enforce strict adherence to **Clean Code** and **SOLID** principles.

### 1. Hexagonal Architecture (Ports and Adapters)
We will isolate the domain core from delivery mechanisms, frameworks, and databases:
* **The Core Domain (`domain/`):** Contains the business model, rules, and invariants. It has **zero** dependencies on external frameworks (no Spring annotations like `@Service`, `@Component`, `@Autowired`, or Spring Data annotations).
* **Ports:**
  - **Inbound Ports (`application/port/inbound/`):** Interfaces defining the API of the system (Use Cases) that external clients or adapters invoke.
  - **Outbound Ports (`application/port/outbound/`):** Interfaces defining the requirements of the system (Repositories, SMS, Queue publish) that the domain core needs to communicate with the outside world.
* **Adapters (`infrastructure/adapter/`):**
  - **Inbound Adapters (`infrastructure/adapter/inbound/`):** Translate outside requests (e.g., SQS Message Listeners, REST Controllers) into inbound port calls.
  - **Outbound Adapters (`infrastructure/adapter/outbound/`):** Implement outbound ports (e.g., Spring Data JPA repositories for PostgreSQL, MongoTemplate repositories for MongoDB).

### 2. Domain-Driven Design (DDD)
* **Ubiquitous Language:** Model naming must strictly match [terminology.md](file:///c:/dev/workspace/data_engineer/data-platform-sandbox/docs/specs/terminology.md) (e.g., `Antenna`, `AntennaEvent`, `TheoreticalCapacity`).
* **Tactical Patterns:**
  - **Aggregates & Entities:** `Antenna` acts as the aggregate root for master data. `AntennaEvent` represents telemetry logs.
  - **Invariants:** Business rules (such as checking theoretical capacity limits or calculating saturation) will live directly inside the domain entities.

### 3. SOLID & Clean Code Rules
* **Single Responsibility (SRP):** Outbound adapters will only map database objects (entities like `AntennaJpaEntity`) to domain entities (like `Antenna`) and write/read them, keeping serialization/mapping concerns separate from domain logic.
* **Open/Closed (OCP):** Introducing new telemetry protocols or analytics backends will be achieved by adding adapters without modifying domain use cases.
* **Dependency Inversion (DIP):** Framework, queue, and database details depend on domain ports, never the other way around. High-level policies (domain) do not depend on low-level details (infrastructure).
* **Granular Testability:** Unit tests for domain logic will run purely in-memory without starting Spring ApplicationContexts, minimizing test execution times.

## Consequences

* **Pros:**
  - **Framework Agnosticism:** Spring Boot can be updated or replaced without affecting the core telemetry rules.
  - **Isolability & Mockability:** High-quality unit tests can verify business rules without requiring test databases or Docker containers.
  - **Clean Dependency Graph:** Circular dependencies are prevented by construction.
* **Cons:**
  - **Boilerplate Mapping:** Requires converting between DB schemas (JPA/MongoDB documents) and pure Domain Models (`Antenna` <-> `AntennaJpaEntity`).
  - **Increased File Count:** Introducing separate ports, services, and adapters increases the volume of classes.

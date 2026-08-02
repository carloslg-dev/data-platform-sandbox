# Functional & Domain Specification: Antenna Telemetry Event Processing

## 1. Domain Context & Business Objectives

The Telecom Data Platform processes high-frequency cell tower telemetry to achieve three business objectives:
1. **Operational Monitoring**: Track live user load and bandwidth usage per antenna/cell.
2. **Network Saturation Alerting**: Detect when an antenna cell exceeds **80% of its theoretical capacity** (`capacityPercentage > 80.0`).
3. **Federated Historical Analytics**: Store long-term immutable events in a Parquet Data Lake accessible via Trino SQL queries.

### Transport Agnosticism Principle
The core domain model must remain agnostic to the event ingestion mechanism. Receiving telemetry via a REST API endpoint or consuming asynchronous Kafka events must execute the **exact same Use Case (`ProcessTelemetryUseCase`)**, enforcing uniform validation rules and domain invariants.

---

## 2. Event Contract (Domain Event Payload)

* **Kafka Topic:** `telemetry.antenna.raw`
* **Format:** JSON UTF-8

### Event Schema Definition
```json
{
  "$schema": "http://json-schema.org/draft-07/schema#",
  "title": "AntennaTelemetryReceivedEvent",
  "type": "object",
  "properties": {
    "eventId": {
      "type": "string",
      "format": "uuid",
      "description": "Unique event identifier used for consumer-side idempotency deduplication."
    },
    "eventType": {
      "type": "string",
      "enum": ["AntennaTelemetryReceived"],
      "description": "Domain event type discriminator."
    },
    "timestamp": {
      "type": "string",
      "format": "date-time",
      "description": "ISO-8601 UTC timestamp of telemetry generation."
    },
    "payload": {
      "type": "object",
      "properties": {
        "antennaId": { "type": "string" },
        "cellId": { "type": "string" },
        "metrics": {
          "type": "object",
          "properties": {
            "connectedUsers": { "type": "integer", "minimum": 0 },
            "bandwidthUsageMbps": { "type": "number", "minimum": 0.0 },
            "capacityPercentage": { "type": "number", "minimum": 0.0, "maximum": 100.0 },
            "isSaturated": { "type": "boolean" }
          },
          "required": ["connectedUsers", "bandwidthUsageMbps", "capacityPercentage", "isSaturated"]
        },
        "status": {
          "type": "string",
          "enum": ["OPERATIONAL", "DEGRADED", "MAINTENANCE", "OFFLINE"]
        }
      },
      "required": ["antennaId", "cellId", "metrics", "status"]
    }
  },
  "required": ["eventId", "eventType", "timestamp", "payload"]
}
```

### Sample Event Payload
```json
{
  "eventId": "evt_987f6a54-321b-4c3d-9e8f-123456789abc",
  "eventType": "AntennaTelemetryReceived",
  "timestamp": "2026-08-02T14:30:00Z",
  "payload": {
    "antennaId": "ANT-BCN-042",
    "cellId": "CELL-8812",
    "metrics": {
      "connectedUsers": 450,
      "bandwidthUsageMbps": 850.5,
      "capacityPercentage": 85.05,
      "isSaturated": true
    },
    "status": "OPERATIONAL"
  }
}
```

---

## 3. Key Domain Rules & Invariants

1. **Idempotent Ingestion Rule:**
   - Duplicate events arriving with the exact same `eventId` must produce **exactly one** persistent record in MongoDB operational store.
   - Secondary ingestion attempts must be safely acknowledged without mutating existing state.

2. **Per-Cell Chronological Ordering Invariant:**
   - Events sharing the same `cellId` or `antennaId` MUST land on the exact same Kafka partition (via partition key hashing).
   - Ingestion consumers processing a given partition execute strictly in arrival sequence, preserving causal timestamp ordering.

3. **Saturation Condition & Alerting:**
   - An antenna cell is flagged as **Saturated** when `capacityPercentage > 80.0%` (or `bandwidthUsageMbps > theoreticalCapacity * 0.8`).
   - Saturated events trigger real-time notification events published to `telemetry.antenna.alerts`.

4. **Historical Replay Isolation:**
   - New or decoupled consumer groups (e.g., `cg-telemetry-audit`) initialized with `auto.offset.reset = earliest` can re-read all historical events from offset 0.
   - Replay consumption must run in isolation without modifying production MongoDB state or triggering false duplicate alerts.

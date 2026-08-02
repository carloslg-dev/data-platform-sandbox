Feature: Asynchronous Antenna Telemetry Processing via Apache Kafka
  As a Data Platform Engineer
  I want antenna telemetry published to Kafka to be processed asynchronously
  So that the core domain remains decoupled from messaging infrastructure

  Background:
    Given the Kafka topic "telemetry.antenna.raw" exists with retention set to 14 days
    And an active antenna with ID "ANT-BCN-042" is registered in PostgreSQL catalog

  Scenario: Successfully consume and persist telemetry event in operational store
    Given a valid "AntennaTelemetryReceived" event for antenna "ANT-BCN-042" with 85% capacity
    When the "KafkaTelemetryConsumerAdapter" receives the event from topic "telemetry.antenna.raw"
    Then the "ProcessTelemetryUseCase" should execute without domain errors
    And the operational state in MongoDB collection "antenna_status" should be updated
    And the cell "CELL-8812" should be flagged as "isSaturated = true"

  Scenario: Guarantee chronological event ordering per cell
    Given multiple telemetry events emitted for cell "CELL-8812" with sequential timestamps
    When events are published using "cellId" as the Kafka partition key
    Then all events for "CELL-8812" must land on the same Kafka partition
    And the consumer group "cg-telemetry-ingestion" must process them in strict timestamp order

  Scenario: Replay historical events for new decoupled consumers
    Given telemetry events stored in "telemetry.antenna.raw" over the past 7 days
    When a new consumer group "cg-telemetry-audit" connects with "auto.offset.reset = earliest"
    Then it should consume all historical events from offset 0 without altering MongoDB state

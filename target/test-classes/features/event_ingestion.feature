Feature: Telemetry Event Ingestion and Idempotency
  As a CEM (Customer Experience Management) system
  I want to receive voice calls and data sessions via SQS FIFO
  So that I can persist them in MongoDB without duplicates

  Scenario: Successfully ingest a telemetry event
    Given an antenna with ID "antenna-101" exists in PostgreSQL
    And the SQS FIFO queue is operational
    When a telemetry event of type "VOICE_CALL" for antenna "antenna-101" with event ID "evt-999" is sent to the queue
    Then the event is processed and persisted in the "antenna_events" collection in MongoDB
    And the stored event contains "duration_ms", "bytes_transferred", and its version is initialized to 0

  Scenario: Prevent duplicate telemetry events (Exactly-Once Idempotency)
    Given a telemetry event with ID "evt-999" already exists in the "antenna_events" collection in MongoDB
    When a duplicate telemetry event with ID "evt-999" is received from the SQS FIFO queue
    Then the system discards the duplicate event
    And no new insert or update operation is executed in MongoDB

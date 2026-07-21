Feature: Concurrency Control and Resiliency
  As a telemetry persistence service
  I want to implement optimistic locking and exponential backoff retry policies
  So that concurrent write collisions are resolved without database deadlock overhead

  Scenario: Resolve concurrency conflict via exponential backoff retry
    Given two concurrent processes attempt to modify telemetry event "evt-999"
    And the first process successfully increments the version to 1
    When the second process attempts to write using version 0
    Then the system catches an "OptimisticLockingFailureException"
    And the system triggers the exponential backoff retry policy
    And eventually the second process's update is successfully merged and persisted

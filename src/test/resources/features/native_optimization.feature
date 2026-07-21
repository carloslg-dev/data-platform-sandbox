Feature: Performance Optimization via C++ Native Processing
  As a high-throughput processing service
  I want to delegate heavy calculations and array parsing to a native C++ module via JNI/FFI
  So that I minimize JVM Garbage Collector Stop-the-World pauses

  Scenario: Calculate aggregate metrics using native library
    Given the JNI native C++ library is loaded in the JVM
    And a set of 100,000 telemetry records is loaded in memory
    When a request is made to calculate the standard deviation of event durations
    Then the calculation is offloaded to the C++ native function
    And the result is computed in native memory without triggering JVM Garbage Collection overhead

Feature: Federated Cross-Catalog Queries with Trino
  As a network performance analyst
  I want to run federated SQL queries in Trino joining PostgreSQL and MongoDB
  So that I can identify saturated cells and perform capacity sizing

  Background:
    Given the PostgreSQL database has the following records in the "antennas" table:
      | id          | location  | type | theoretical_capacity | status |
      | antenna-101 | Madrid    | 5G   | 1000                 | ACTIVE |
      | antenna-102 | Barcelona | 4G   | 500                  | ACTIVE |
    And the MongoDB "antenna_events" collection has the following telemetry data:
      | event_id | antenna_id  | event_type   | bytes_transferred |
      | evt-1    | antenna-101 | DATA_SESSION | 850               |
      | evt-2    | antenna-102 | DATA_SESSION | 200               |

  Scenario: Retrieve antennas exceeding 80% of their theoretical capacity
    When I execute the following federated query in Trino:
    """
    SELECT a.id, a.location, SUM(e.bytes_transferred) as total_traffic
    FROM postgresql.public.antennas a
    JOIN mongodb.telemetry.antenna_events e ON a.id = e.antenna_id
    GROUP BY a.id, a.location, a.theoretical_capacity
    HAVING SUM(e.bytes_transferred) > (a.theoretical_capacity * 0.8)
    """
    Then the Trino query result should return:
      | id          | location  | total_traffic |
      | antenna-101 | Madrid    | 850           |

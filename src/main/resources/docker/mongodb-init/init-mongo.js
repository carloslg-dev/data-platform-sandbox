// Initialize MongoDB database and collection with unique index for telemetry events
db = db.getSiblingDB('telemetry');

db.createCollection('antenna_events');

db.antenna_events.createIndex({ "event_id": 1 }, { unique: true });

print("MongoDB telemetry database initialized with unique index on event_id.");

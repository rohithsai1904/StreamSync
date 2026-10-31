
# StreamSync

StreamSync replicates data between two PostgreSQL databases using change data capture (CDC), and includes a reconciliation tool that finds and repairs any differences between them.

Debezium reads row changes from a source Postgres and publishes them to Kafka. A Spring Boot service consumes those events and applies them to a target Postgres, recording each processed Kafka offset so that redelivered events are never applied twice. For drift the stream can't fix on its own, a comparison tool (DCT) hashes every row on both sides, reports the differences, and can seed the missing or changed rows.

## Features

- **Real-time replication.** Inserts, updates and deletes on the source appear on the target through Debezium and Kafka.
- **Idempotent processing.** Each event's `(topic, partition, offset)` is stored in `processed_events` in the same transaction as the data change, so duplicate deliveries are skipped.
- **Manual offset commits.** An offset is acknowledged only after the target transaction commits.
- **Automatic schema creation.** Target tables are created from the Debezium event schema, and columns added on the source are added on the target.
- **Data comparison (DCT).** Per-row `md5` hashes are compared across both databases to list rows that are missing, extra or mismatched.
- **Seeding.** Rows found by DCT are inserted or updated to bring the databases back in line, on demand or on a schedule.

## Architecture

```
┌──────────────┐    ┌──────────────┐    ┌──────────────┐    ┌────────────────────────┐    ┌──────────────┐
│ Source       │    │ Debezium     │    │ Kafka        │    │ Replicator             │    │ Target       │
│ PostgreSQL   │───▶│ Connect      │───▶│              │───▶│ (Spring Boot)          │───▶│ PostgreSQL   │
└──────┬───────┘    └──────────────┘    └──────────────┘    └────────────────────────┘    └──────┬───────┘
       │                                                                                         │
       │                                                                                         │
       └───────────────────────────────┐             ┌───────────────────────────────────────────┘
                                       ▼             ▼
                              ┌─────────────────────────────┐
                              │ DCT (Data Comparison Tool)  │
                              │ Compare row hashes          │
                              └──────────────┬──────────────┘
                                             │
                                             ▼
                              ┌─────────────────────────────┐
                              │ Identify data differences  │
                              └──────────────┬──────────────┘
                                             │
                                             ▼
                              ┌─────────────────────────────┐
                              │ Seeding / Data Repair       │
                              │ Fix missing or mismatched   │
                              │ rows                        │
                              └─────────────────────────────┘
```
### Streaming
1. Debezium reads the source write-ahead log through a `pgoutput` replication slot and publishes one message per change to a topic named `app.public.<table>`.
2. `EventParser` consumes each message.
3. `EventProcessingService` runs one target-database transaction that:
- inserts the event's `(topic, partition, offset)` into `processed_events` and stops if it already exists, then
- applies the change with `InsertOperation`, `UpdateOperation` or `DeleteOperation`.
4. The message is acknowledged after the transaction commits.

Snapshot and create events are applied as inserts. If the target table doesn't exist it is created from the Debezium schema.

### Comparison and seeding
1. `DCT` runs `md5(row(t.*)::text)` for every row in the table on the source and the target, keyed by `id`.
2. The hashes are diffed into rows missing in the target, rows only in the target, and rows whose hashes differ.
3. `Seeding` inserts the missing rows, updates the mismatched rows on the target, and inserts target-only rows into the source.
4. `scheduleSeed` repeats the compare-and-seed cycle at a fixed interval.

## Tech stack

| Component | Technology |
|---|---|
| Application | Java 27, Spring Boot 4.1.1, Spring Kafka, Spring JDBC |
| Databases | PostgreSQL 16 with logical replication |
| Change data capture | Debezium 2.7.3 on Kafka Connect |
| Messaging | Apache Kafka 7.7.1 (KRaft mode) |
| Local environment | Docker Compose |

## Project structure

```
compose.yaml Source and target Postgres, Kafka, Debezium Connect
postgres-connector.json Debezium connector configuration
postgres/init.sql Source schema and sample data
postgres/target-init.sql Creates the processed_events table on the target
replicator/ Spring Boot application
DCT/ Row hashing and comparison
seeding/ Repair of differences found by DCT
operation/ Insert, update and delete handlers for CDC events
source/, target/ Database configuration, idempotency check, table service
utils/ SQL builders, Debezium schema parser, type mapper
```

## Getting started

### Prerequisites
- Docker and Docker Compose
- JDK 27 (a Maven wrapper is included)

### 1. Start the infrastructure
```bash
docker compose up -d
```

| Service | Port |
|---|---|
| Source Postgres | 5432 |
| Target Postgres | 5433 |
| Kafka | 9092 |
| Kafka Connect | 8083 |

### 2. Register the Debezium connector
```bash
curl -X POST -H "Content-Type: application/json" \
--data @postgres-connector.json \
http://localhost:8083/connectors
```

### 3. Choose the topic to consume
Set the topic in `replicator/src/main/resources/application.properties` to the table you want to replicate. For the sample `customers` table:
```properties
app.kafka.topic=app.public.customers
```

### 4. Run the replicator
```bash
cd replicator
./mvnw spring-boot:run -Dspring-boot.run.arguments="listen"
```

Insert or update a row in the source `customers` table and it appears in the target.

## Usage

| Command | Description |
|---|---|
| `listen` | Consume the Kafka topic and apply changes to the target. |
| `DCT <table>` | Compare the table on both databases, print the differences, and ask whether to seed. |
| `seed <table>` | Compare and repair the table without prompting. |
| `scheduleSeed <table> <delayMin> <intervalMin>` | Compare and repair repeatedly, starting after `delayMin` minutes and repeating every `intervalMin` minutes. |

Pass the command through `spring-boot.run.arguments`:
```bash
./mvnw spring-boot:run -Dspring-boot.run.arguments="DCT customers"
./mvnw spring-boot:run -Dspring-boot.run.arguments="scheduleSeed customers 1 30"
```

## Configuration

All settings are in `replicator/src/main/resources/application.properties`.

| Property | Purpose |
|---|---|
| `app.kafka.topic` | Topic to consume |
| `spring.kafka.consumer.group-id` | Consumer group |
| `source.datasource.*` | Source database connection |
| `target.datasource.*` | Target database connection |

Debezium settings (replication slot, publication, included tables) are in `postgres-connector.json`.

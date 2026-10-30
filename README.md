# StreamSync
Postgres-to-Postgres replication built on change data capture (CDC), with a reconciliation job that detects and repairs drift between the two databases.
Debezium streams row changes from a source Postgres into Kafka. A Spring Boot service applies them to a target Postgres with idempotent, transactional processing. A separate comparison and seeding path (DCT) finds rows that the stream missed or got wrong and fixes them.
> **Status:** work in progress. Sections marked **(planned)** describe the intended end state and are not implemented yet. See the [Roadmap](#roadmap) for the current state of each item.
## Architecture
```
                                  ┌──────────────────────────────────────┐
                                  │            replicator (Spring Boot)  │
┌────────────┐   ┌──────────┐   ┌─┴────────┐   ┌──────────────────────┐  │   ┌────────────┐
│  source    │──▶│ Debezium │──▶│  Kafka   │──▶│ EventParser          │──┼──▶│  target    │
│  Postgres  │   │ (pgoutput)   │  topic   │   │ (manual ack)         │  │   │  Postgres  │
└─────┬──────┘   └──────────┘   └──────────┘   │  └▶ EventProcessing  │  │   └─────┬──────┘
      │                                         │     ├ processed_events│  │         │
      │          reconciliation (DCT + seeding) │     └ Insert/Update/ │  │         │
      └─────────────────────────────────────────┼──────  Delete op     ─┘  │─────────┘
                                                └──────────────────────────┘
```
### Streaming path
1. Debezium reads the source WAL through a logical replication slot (`pgoutput`) and publishes one Kafka message per change to `app.public.<table>`.
2. `EventParser` consumes each message with manual acknowledgement.
3. `EventProcessingService` opens a single target-database transaction that:
   - inserts `(topic, partition, offset)` into `processed_events`, and skips the event if that row already exists (idempotency), then
   - applies the change through `InsertOperation`, `UpdateOperation` or `DeleteOperation`.
4. The offset is acknowledged only after the transaction commits.
Delivery is at-least-once. Because the dedupe record and the data change commit together, the effect on the target is exactly-once.
Snapshot (`r`) and create (`c`) events are inserts. If the target table does not exist, it is created from the Debezium schema, and new source columns are added on the target.
### Reconciliation path (DCT + seeding)
1. **DCT** computes a hash for every row on both sides (`md5(row(t.*)::text)`) and diffs them into three sets: missing in target, extra in target, and mismatched.
2. **Seeding** repairs the differences: it inserts missing rows and updates mismatched rows on the target.
3. `scheduleSeed` runs this on a fixed interval.
Today the comparison is a flat per-row hash. A Merkle-tree comparison that narrows differences by key range, so that large tables don't need every row hash transferred, is **(planned)**.
## Tech stack
| Layer | Technology |
|---|---|
| Language / framework | Java 27, Spring Boot 4.1.1, Spring Kafka, Spring JDBC |
| Source / target | PostgreSQL 16 (logical replication) |
| CDC | Debezium 2.7.3 (Kafka Connect) |
| Messaging | Apache Kafka 7.7.1 (KRaft, single broker) |
| Local infra | Docker Compose |
## Repository layout
```
compose.yaml               Source Postgres, target Postgres, Kafka, Debezium Connect
postgres-connector.json    Debezium connector configuration
postgres/init.sql          Source schema and sample data
replicator/                Spring Boot application
  .../DCT/                 Row hashing and comparison
  .../seeding/             Repair of differences found by DCT
  .../operation/           Insert / Update / Delete handlers for CDC events
  .../target/              Target DB config, idempotency, table service
  .../source/              Source DB config
  .../utils/               DDL/DML builders, Debezium schema parser, type mapper
```
## Getting started
### Prerequisites
- Docker and Docker Compose
- JDK 27 (the Maven wrapper is included)
### 1. Start the infrastructure
```bash
docker compose up -d
```
Ports: source Postgres `5432`, target Postgres `5433`, Kafka `9092`, Kafka Connect `8083`.
### 2. Register the Debezium connector
```bash
curl -X POST -H "Content-Type: application/json" \
  --data @postgres-connector.json \
  http://localhost:8083/connectors
```
### 3. Create the target bookkeeping table
The target database needs this table for idempotency:
```sql
CREATE TABLE IF NOT EXISTS processed_events (
  topic         TEXT        NOT NULL,
  partition_id  INTEGER     NOT NULL,
  offset_value  BIGINT      NOT NULL,
  processed_at  TIMESTAMPTZ NOT NULL,
  PRIMARY KEY (topic, partition_id, offset_value)
);
```
Creating this automatically through an init script is **(planned)**.
### 4. Run the replicator
```bash
cd replicator
./mvnw spring-boot:run -Dspring-boot.run.arguments="listen"
```
## Usage
The application takes a mode and, for most modes, a table name.
| Command | What it does |
|---|---|
| `listen` | Consume the Kafka topic and apply changes to the target. |
| `DCT <table>` | Compare source and target, print the differences, and ask whether to seed. |
| `seed <table>` | Compare and repair immediately, without prompting. |
| `scheduleSeed <table> <delayMin> <intervalMin>` | Compare and repair repeatedly on a schedule. |
Example:
```bash
./mvnw spring-boot:run -Dspring-boot.run.arguments="DCT customers"
```
Configuration lives in `replicator/src/main/resources/application.properties` (topic, consumer group, and source/target datasource URLs).
## Design decisions
- **Transactional dedupe instead of Kafka transactions.** Recording the offset in the target database in the same transaction as the write gives exactly-once effect without coordinating Kafka and Postgres.
- **Manual acknowledgement after commit.** A crash between commit and ack causes a redelivery, which the dedupe table absorbs. A crash before commit loses nothing.
- **Reconciliation alongside streaming.** CDC alone cannot fix drift caused by dropped events, manual edits on the target, or an outage longer than the WAL retention. DCT gives a way to detect and repair that.
- **Dynamic target schema.** The target table is derived from the Debezium event schema, so new tables and added columns need no manual DDL.
## Known limitations
These are being addressed; see the roadmap.
- The type mapper handles only basic Debezium types. Logical types (timestamps, dates, decimals, UUIDs, JSON) are not mapped correctly, which also makes the hash comparison report false mismatches.
- Target tables are created without a primary key.
- Table and column names are not consistently validated or quoted before being used in SQL.
- Row ids are read as 32-bit integers, so `BIGINT` ids can overflow.
- Failed events are not routed to a dead-letter topic.
- Seeding currently copies rows that exist only on the target back to the source, which can resurrect rows deleted at the source before the delete event arrives.
- Automated tests are minimal and require running infrastructure.
## Roadmap
Status is `done`, `in progress` or `planned`.
### Reliability and correctness
- [ ] Fix `compose.yaml` so the two Postgres services have distinct container names and volumes, and update `depends_on` and the connector host to match (planned)
- [ ] Enable schemas in the Connect JSON converter so events carry `payload` and `schema` (planned)
- [ ] Create `processed_events` and the sample `orders` table in init scripts (planned)
- [ ] Start the Kafka listener only in `listen` mode, and restart it after a scheduled seed (planned)
- [ ] Add a dead-letter topic with bounded retries so failing events are never silently skipped (planned)
- [ ] Make seeding one-directional by default (source to target), with the reverse direction behind an explicit flag (planned)
### Data fidelity
- [ ] Map Debezium logical types (timestamp, date, decimal, UUID, JSON) to native Postgres types (planned)
- [ ] Create target tables with the source primary key (planned)
- [ ] Validate and quote all table and column identifiers (planned)
- [ ] Read ids as `long` and support non-`id` primary keys (planned)
### Reconciliation
- [ ] Replace the flat row-hash comparison with a Merkle tree over key ranges (planned)
- [ ] Hash a canonical, column-order-independent representation of each row (planned)
### Quality
- [ ] Integration tests with Testcontainers (Postgres, Kafka) covering insert, update, delete, redelivery and drift repair (planned)
- [ ] Replace `System.out` with structured logging (planned)
- [ ] Benchmark: events per second and reconciliation time for N rows, with results recorded here (planned)
- [ ] Remove unused dependencies (web, batch) and the committed `.DS_Store` (planned)
## License
No license has been chosen yet.

# StreamSync
StreamSync replicates data between two PostgreSQL databases using change data capture (CDC), and includes a reconciliation tool that finds and repairs any differences between them.
Debezium reads row changes from a source Postgres and publishes them to Kafka. A Spring Boot service consumes those events and applies them to a target Postgres, recording each processed Kafka offset so that redelivered events are never applied twice. For drift the stream can't fix on its own, a comparison tool hashes the rows on both sides, reports the differences, and can seed the missing or changed rows.
## Features
- **Real-time replication.** Inserts, updates and deletes on the source appear on the target through Debezium and Kafka.
- **Automatic table pickup.** A publication covering the whole `public` schema means new tables are captured without restarting the connector, and the consumer subscribes to all `app.public.*` topics by pattern.
- **Idempotent processing.** Each event's `(topic, partition, offset)` is stored in `processed_events` in the same transaction as the data change, so duplicate deliveries are skipped.
- **Manual offset commits.** An offset is acknowledged only after the target transaction commits.
- **Automatic schema creation.** Target tables are created from the Debezium event schema, and columns added on the source are added on the target.
- **Two comparison modes.**
  - `DCT` hashes every row on both sides.
  - `merkleDCT` compares key ranges using count and XOR-of-hashes aggregates, and descends only into ranges that differ, so work scales with the number of differences.
- **Seeding.** Rows found by a comparison are inserted or updated to bring the databases back in line, on demand or on a schedule.
- **Automatic connector registration.** A `connector-init` container registers or updates the Debezium connector on every `docker compose up`.
## Architecture
```
┌──────────────┐    ┌──────────────┐    ┌──────────────┐    ┌────────────────────────┐    ┌──────────────┐
│ Source       │    │ Debezium     │    │ Kafka        │    │ Replicator             │    │ Target       │
│ PostgreSQL   │───▶│ Connect      │───▶│              │───▶│ (Spring Boot)          │───▶│ PostgreSQL   │
└──────┬───────┘    └──────────────┘    └──────────────┘    └────────────────────────┘    └──────┬───────┘
       │                                                                                         │
       └───────────────────────────────┐             ┌───────────────────────────────────────────┘
                                       ▼             ▼
                              ┌─────────────────────────────┐
                              │ DCT / Merkle DCT            │
                              │ Compare source and target   │
                              └──────────────┬──────────────┘
                                             │
                                             ▼
                              ┌─────────────────────────────┐
                              │ Seeding / data repair       │
                              │ Fix missing or mismatched   │
                              │ rows                        │
                              └─────────────────────────────┘
```
### Streaming
1. Debezium reads the source write-ahead log through the `debezium_app` replication slot and the `dbz_publication` publication (`pgoutput`), and publishes one message per change to a topic named `app.public.<table>`. Topics are created on first use.
2. `EventParser` consumes every topic matching `app.kafka.topic` (a regex, `app\.public\..*` by default).
3. `EventProcessingService` runs one target-database transaction that:
   - inserts the event's `(topic, partition, offset)` into `processed_events` and stops if it already exists, then
   - applies the change with `InsertOperation`, `UpdateOperation` or `DeleteOperation`.
4. The message is acknowledged after the transaction commits.
Snapshot and create events are applied as inserts. If the target table doesn't exist it is created from the Debezium schema. Updates write the row's new values and do not need the previous row image, so the source tables can use the default replica identity.
### Comparison and seeding
**`DCT`** runs `md5(row(t.*)::text)` for every row on both sides, keyed by `id`, and diffs the two sets into rows missing in the target, rows only in the target, and rows whose hashes differ.
**`merkleDCT`** avoids reading every row:
1. It finds the minimum and maximum `id` across both databases.
2. For a key range it asks each database for a row count and an XOR of per-row hashes for each of 16 equal sub-ranges, computed in SQL.
3. Sub-ranges with equal aggregates are skipped. Differing sub-ranges are split again.
4. Once a range is 2,000 ids wide or less, it compares rows individually.
**Seeding** inserts the missing rows and updates the mismatched rows on the target, and inserts target-only rows into the source. `scheduleSeed` repeats compare-and-seed at a fixed interval.
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
compose.yaml                 Source and target Postgres, Kafka, Connect, connector-init
postgres-connector.json      Debezium connector definition (name + config)
connector-init/              Container that registers the connector on startup
postgres/init.sql            Source schema, sample data and the dbz_publication publication
postgres/target-init.sql     Creates the processed_events table on the target
replicator/                  Spring Boot application
  DCT/                       Per-row hash comparison
  MerkleDCT/                 Range-based hash comparison
  seeding/                   Repair of differences found by a comparison
  operation/                 Insert, update and delete handlers for CDC events
  source/, target/           Database configuration, idempotency check, table service
  utils/                     SQL builders, Debezium schema parser, type mapper
```
## Getting started
### Prerequisites
- Docker and Docker Compose
- JDK 27 (a Maven wrapper is included)
### 1. Start the infrastructure
```bash
docker compose up -d --build
```
| Service | Port |
|---|---|
| Source Postgres | 5432 |
| Target Postgres | 5433 |
| Kafka | 9092 |
| Kafka Connect | 8083 |
The `connector-init` container registers the Debezium connector `postgres-cdc` once Kafka Connect is healthy. Check it with:
```bash
curl localhost:8083/connectors/postgres-cdc/status
```
If the source database volume was created before `init.sql` defined the publication, create it once:
```bash
docker exec cdc-src-postgres psql -U app -d app -c \
  "DROP PUBLICATION IF EXISTS dbz_publication; CREATE PUBLICATION dbz_publication FOR TABLES IN SCHEMA public;"
```
Alternatively, run `docker compose down -v` to start from empty volumes.
### 2. Run the replicator
```bash
cd replicator
./mvnw spring-boot:run -Dspring-boot.run.arguments="listen"
```
### 3. Try it
Create a table and insert a row on the source:
```sql
CREATE TABLE orders (id BIGSERIAL PRIMARY KEY, item TEXT NOT NULL);
INSERT INTO orders (item) VALUES ('first');
```
The topic `app.public.orders` is created, and the table and row appear on the target (port 5433) once the consumer picks up the new topic.
## Usage
| Command | Description |
|---|---|
| `listen` | Consume the Kafka topics and apply changes to the target. |
| `DCT <table>` | Compare the table with per-row hashes, print the differences, and ask whether to seed. |
| `merkleDCT <table>` | Same as `DCT`, using the range-based comparison. |
| `seed <table>` | Compare and repair the table without prompting. |
| `scheduleSeed <table> <delayMin> <intervalMin>` | Compare and repair repeatedly, starting after `delayMin` minutes and repeating every `intervalMin` minutes. |
Pass the command through `spring-boot.run.arguments`:
```bash
./mvnw spring-boot:run -Dspring-boot.run.arguments="merkleDCT customers"
./mvnw spring-boot:run -Dspring-boot.run.arguments="scheduleSeed customers 1 30"
```
Comparison and seeding expect each table to have a numeric `id` primary key.
## Configuration
Application settings are in `replicator/src/main/resources/application.properties`.
| Property | Purpose |
|---|---|
| `app.kafka.topic` | Regex of topics to consume (default `app\.public\..*`) |
| `spring.kafka.consumer.group-id` | Consumer group |
| `spring.kafka.consumer.auto-offset-reset` | Where a new topic starts (`earliest`) |
| `spring.kafka.consumer.properties.metadata.max.age.ms` | How often new topics are discovered |
| `source.datasource.*` | Source database connection |
| `target.datasource.*` | Target database connection |
Debezium settings (replication slot, publication, included tables, topic creation) are in `postgres-connector.json`. The connector name used by `connector-init` is `postgres-cdc`.
## Design notes
- **Transactional dedupe.** Storing the Kafka offset in the target database in the same transaction as the write gives an exactly-once effect without coordinating Kafka and Postgres transactions.
- **Acknowledge after commit.** A crash between commit and acknowledgement causes a redelivery, which `processed_events` absorbs. A crash before commit loses nothing.
- **Reconciliation alongside streaming.** CDC alone cannot repair drift from dropped events, manual edits on the target, or an outage longer than WAL retention. The comparison tools detect and repair it.
- **Range hashing.** The XOR of 64-bit row hashes is order-independent, and including the row count in each aggregate makes accidental matches between different ranges less likely.
Displaying README.md.
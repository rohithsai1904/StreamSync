CREATE TABLE processed_events (
    topic          VARCHAR(255) NOT NULL,
    partition_id   INTEGER NOT NULL,
    offset_value   BIGINT NOT NULL,
    processed_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    PRIMARY KEY (topic, partition_id, offset_value)
);
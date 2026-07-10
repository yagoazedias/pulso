CREATE TABLE record (
    id             BIGSERIAL PRIMARY KEY,
    record_type_id INTEGER NOT NULL REFERENCES record_type(id),
    source_id      INTEGER REFERENCES source(id),
    device_id      INTEGER REFERENCES device(id),
    unit_id        INTEGER REFERENCES unit(id),
    value          TEXT,
    creation_date  TIMESTAMPTZ,
    start_date     TIMESTAMPTZ NOT NULL,
    end_date       TIMESTAMPTZ NOT NULL
);
--;;
CREATE INDEX idx_record_type      ON record (record_type_id);
--;;
CREATE INDEX idx_record_start     ON record (start_date);
--;;
CREATE INDEX idx_record_type_date ON record (record_type_id, start_date);
--;;
CREATE INDEX idx_record_source    ON record (source_id);
--;;
CREATE TABLE record_metadata (
    id        BIGSERIAL PRIMARY KEY,
    record_id BIGINT NOT NULL REFERENCES record(id) ON DELETE CASCADE,
    key       TEXT NOT NULL,
    value     TEXT
);
--;;
CREATE INDEX idx_record_metadata_record ON record_metadata (record_id);

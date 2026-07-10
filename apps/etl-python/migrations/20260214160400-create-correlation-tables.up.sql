CREATE TABLE correlation (
    id            BIGSERIAL PRIMARY KEY,
    type          TEXT NOT NULL,
    source_id     INTEGER REFERENCES source(id),
    device_id     INTEGER REFERENCES device(id),
    creation_date TIMESTAMPTZ,
    start_date    TIMESTAMPTZ NOT NULL,
    end_date      TIMESTAMPTZ NOT NULL
);
--;;
CREATE INDEX idx_correlation_type  ON correlation (type);
--;;
CREATE INDEX idx_correlation_start ON correlation (start_date);
--;;
CREATE TABLE correlation_metadata (
    id             BIGSERIAL PRIMARY KEY,
    correlation_id BIGINT NOT NULL REFERENCES correlation(id) ON DELETE CASCADE,
    key            TEXT NOT NULL,
    value          TEXT
);
--;;
CREATE INDEX idx_corr_metadata_corr ON correlation_metadata (correlation_id);
--;;
CREATE TABLE correlation_record (
    id             BIGSERIAL PRIMARY KEY,
    correlation_id BIGINT NOT NULL REFERENCES correlation(id) ON DELETE CASCADE,
    record_id      BIGINT NOT NULL REFERENCES record(id) ON DELETE CASCADE
);
--;;
CREATE INDEX idx_corr_record_corr   ON correlation_record (correlation_id);
--;;
CREATE INDEX idx_corr_record_record ON correlation_record (record_id);

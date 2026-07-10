CREATE TABLE workout (
    id                       BIGSERIAL PRIMARY KEY,
    activity_type            TEXT NOT NULL,
    duration                 DOUBLE PRECISION,
    duration_unit            TEXT,
    total_distance           DOUBLE PRECISION,
    total_distance_unit      TEXT,
    total_energy_burned      DOUBLE PRECISION,
    total_energy_burned_unit TEXT,
    source_id                INTEGER REFERENCES source(id),
    device_id                INTEGER REFERENCES device(id),
    creation_date            TIMESTAMPTZ,
    start_date               TIMESTAMPTZ NOT NULL,
    end_date                 TIMESTAMPTZ NOT NULL
);
--;;
CREATE INDEX idx_workout_type  ON workout (activity_type);
--;;
CREATE INDEX idx_workout_start ON workout (start_date);
--;;
CREATE TABLE workout_metadata (
    id         BIGSERIAL PRIMARY KEY,
    workout_id BIGINT NOT NULL REFERENCES workout(id) ON DELETE CASCADE,
    key        TEXT NOT NULL,
    value      TEXT
);
--;;
CREATE INDEX idx_workout_metadata_workout ON workout_metadata (workout_id);
--;;
CREATE TABLE workout_event (
    id            BIGSERIAL PRIMARY KEY,
    workout_id    BIGINT NOT NULL REFERENCES workout(id) ON DELETE CASCADE,
    type          TEXT NOT NULL,
    date          TIMESTAMPTZ,
    duration      DOUBLE PRECISION,
    duration_unit TEXT
);
--;;
CREATE INDEX idx_workout_event_workout ON workout_event (workout_id);
--;;
CREATE TABLE workout_statistics (
    id         BIGSERIAL PRIMARY KEY,
    workout_id BIGINT NOT NULL REFERENCES workout(id) ON DELETE CASCADE,
    type       TEXT NOT NULL,
    start_date TIMESTAMPTZ,
    end_date   TIMESTAMPTZ,
    average    DOUBLE PRECISION,
    minimum    DOUBLE PRECISION,
    maximum    DOUBLE PRECISION,
    sum        DOUBLE PRECISION,
    unit       TEXT
);
--;;
CREATE INDEX idx_workout_stats_workout ON workout_statistics (workout_id);
--;;
CREATE TABLE workout_route (
    id          BIGSERIAL PRIMARY KEY,
    workout_id  BIGINT NOT NULL REFERENCES workout(id) ON DELETE CASCADE,
    source_name TEXT,
    start_date  TIMESTAMPTZ,
    end_date    TIMESTAMPTZ,
    file_path   TEXT
);
--;;
CREATE INDEX idx_workout_route_workout ON workout_route (workout_id);

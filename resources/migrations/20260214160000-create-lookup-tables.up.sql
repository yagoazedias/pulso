CREATE TABLE source (
    id       SERIAL PRIMARY KEY,
    name     TEXT NOT NULL,
    version  TEXT,
    UNIQUE (name, version)
);
--;;
CREATE TABLE device (
    id       SERIAL PRIMARY KEY,
    raw_text TEXT NOT NULL UNIQUE
);
--;;
CREATE TABLE record_type (
    id         SERIAL PRIMARY KEY,
    identifier TEXT NOT NULL UNIQUE
);
--;;
CREATE TABLE unit (
    id   SERIAL PRIMARY KEY,
    name TEXT NOT NULL UNIQUE
);

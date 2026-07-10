CREATE TABLE user_profile (
    id                  SERIAL PRIMARY KEY,
    date_of_birth       DATE,
    biological_sex      TEXT,
    blood_type          TEXT,
    fitzpatrick_skin    TEXT,
    cardio_fitness_meds TEXT,
    export_date         TIMESTAMPTZ,
    locale              TEXT
);

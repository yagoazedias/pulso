CREATE TABLE activity_summary (
    id                        BIGSERIAL PRIMARY KEY,
    date_components           DATE NOT NULL UNIQUE,
    active_energy_burned      DOUBLE PRECISION,
    active_energy_burned_goal DOUBLE PRECISION,
    active_energy_burned_unit TEXT,
    apple_move_time           DOUBLE PRECISION,
    apple_move_time_goal      DOUBLE PRECISION,
    apple_exercise_time       DOUBLE PRECISION,
    apple_exercise_time_goal  DOUBLE PRECISION,
    apple_stand_hours         DOUBLE PRECISION,
    apple_stand_hours_goal    DOUBLE PRECISION
);
--;;
CREATE INDEX idx_activity_date ON activity_summary (date_components);

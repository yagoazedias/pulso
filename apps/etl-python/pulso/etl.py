import logging
import time

from pulso import db
from pulso.loader import activity, correlations, lookups, profile, records, workouts
from pulso.xml import parser

logger = logging.getLogger(__name__)

PROGRESS_EVERY = 100000


def execute(pool, xml_file, batch_size, on_element=None):
    """Runs the full ETL pipeline: parse XML -> transform -> load into
    PostgreSQL. When on_element is provided, the old log-every-100k
    progress reporting is disabled (the caller is driving its own UI)."""
    logger.info("Starting ETL pipeline file=%s batch_size=%s", xml_file, batch_size)
    start = time.time()
    use_log_progress = on_element is None

    db.truncate_all(pool)
    lookups.reset_caches()
    profile.reset_state()

    record_batchers = records.make_batchers(pool, batch_size)
    workout_batchers = workouts.make_batchers(pool, batch_size)
    corr_batchers = correlations.make_batchers(pool, batch_size)
    activity_batcher = activity.make_batcher(pool, batch_size)
    counters = {"records": 0, "workouts": 0, "correlations": 0, "activities": 0}

    def handler(element, locale):
        tag = element.tag
        if tag == "ExportDate":
            profile.save_export_date(element)
            if on_element:
                on_element("ExportDate")
        elif tag == "Me":
            profile.save_profile(pool, element, locale)
            if on_element:
                on_element("Me")
        elif tag == "Record":
            records.process(pool, record_batchers, element)
            counters["records"] += 1
            if use_log_progress and counters["records"] % PROGRESS_EVERY == 0:
                logger.info("Progress: %s records processed", counters["records"])
            if on_element:
                on_element("Record")
        elif tag == "Workout":
            workouts.process(pool, workout_batchers, element)
            counters["workouts"] += 1
            if on_element:
                on_element("Workout")
        elif tag == "Correlation":
            correlations.process(pool, corr_batchers, element)
            counters["correlations"] += 1
            if on_element:
                on_element("Correlation")
        elif tag == "ActivitySummary":
            activity.process(activity_batcher, element)
            counters["activities"] += 1
            if on_element:
                on_element("ActivitySummary")
        else:
            logger.debug("Skipping element tag=%s", tag)

    parser.parse_health_data(xml_file, handler)

    records.flush(record_batchers)
    workouts.flush(workout_batchers)
    correlations.flush(pool, corr_batchers)
    activity_batcher.flush()

    elapsed_ms = int((time.time() - start) * 1000)
    logger.info(
        "ETL complete! %s elapsed_ms=%s elapsed_min=%.1f",
        counters, elapsed_ms, elapsed_ms / 60000.0,
    )
    return counters

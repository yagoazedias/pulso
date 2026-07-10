import logging

from pulso import db
from pulso.xml import transform

logger = logging.getLogger(__name__)

_export_date = None


def reset_state():
    global _export_date
    _export_date = None


def save_export_date(element):
    global _export_date
    data = transform.export_date_element_to_map(element)
    _export_date = data["export_date"]
    logger.info("Export date: %s", _export_date)


def save_profile(pool, element, locale):
    data = transform.me_element_to_map(element, locale)
    logger.info("Saving user profile - DOB: %s Sex: %s", data["date_of_birth"], data["biological_sex"])
    with db.get_conn(pool) as conn:
        with conn.cursor() as cur:
            cur.execute(
                """INSERT INTO user_profile
                       (date_of_birth, biological_sex, blood_type,
                        fitzpatrick_skin, cardio_fitness_meds,
                        export_date, locale)
                   VALUES (%s, %s, %s, %s, %s, %s, %s)""",
                (
                    data["date_of_birth"],
                    data["biological_sex"],
                    data["blood_type"],
                    data["fitzpatrick_skin"],
                    data["cardio_fitness_meds"],
                    _export_date,
                    data["locale"],
                ),
            )
        conn.commit()

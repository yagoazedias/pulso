import os

import pytest

from pulso import etl

pytestmark = pytest.mark.integration

FIXTURE = os.path.join(os.path.dirname(__file__), "..", "fixtures", "small-export.xml")


def test_execute_full_pipeline(test_ds, count_rows):
    result = etl.execute(test_ds, FIXTURE, 5000)

    # Fixture contains 2 top-level records (nested correlation records not counted here)
    assert result["records"] == 2
    assert result["workouts"] == 1
    assert result["correlations"] == 1
    assert result["activities"] == 1

    # Records: 2 top-level + 2 nested in correlation = 4 total
    assert count_rows("record") == 4
    # Record metadata: 2 from HeartRate, 0 from ActiveEnergyBurned, 0 from nested records
    assert count_rows("record_metadata") == 2
    assert count_rows("workout") == 1
    assert count_rows("workout_metadata") == 1
    assert count_rows("workout_event") == 1
    assert count_rows("workout_statistics") == 1
    assert count_rows("workout_route") == 1
    assert count_rows("correlation") == 1
    assert count_rows("correlation_metadata") == 1
    assert count_rows("correlation_record") == 2
    assert count_rows("activity_summary") == 1
    assert count_rows("user_profile") == 1


def test_execute_is_idempotent(test_ds, count_rows):
    result1 = etl.execute(test_ds, FIXTURE, 5000)
    result2 = etl.execute(test_ds, FIXTURE, 5000)

    assert result1 == result2
    assert count_rows("record") == 4
    assert count_rows("workout") == 1
    assert count_rows("correlation") == 1
    assert count_rows("activity_summary") == 1
    assert count_rows("user_profile") == 1

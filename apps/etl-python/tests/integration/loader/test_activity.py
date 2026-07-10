import xml.etree.ElementTree as ET

import pytest

from pulso.loader import activity

pytestmark = pytest.mark.integration


def test_process_activity_summary(test_ds, count_rows, select_all):
    batcher = activity.make_batcher(test_ds, 10)
    element = ET.Element("ActivitySummary", {
        "dateComponents": "2025-01-15",
        "activeEnergyBurned": "450.5", "activeEnergyBurnedGoal": "420.0", "activeEnergyBurnedUnit": "kcal",
        "appleMoveTime": "30.5", "appleMoveTimeGoal": "30.0",
        "appleExerciseTime": "25.0", "appleExerciseTimeGoal": "30.0",
        "appleStandHours": "10.0", "appleStandHoursGoal": "12.0",
    })

    activity.process(batcher, element)
    batcher.flush()

    assert count_rows("activity_summary") == 1
    row = select_all("activity_summary")[0]
    assert str(row["date_components"]) == "2025-01-15"
    assert row["active_energy_burned"] == 450.5
    assert row["active_energy_burned_goal"] == 420.0
    assert row["active_energy_burned_unit"] == "kcal"
    assert row["apple_move_time"] == 30.5
    assert row["apple_move_time_goal"] == 30.0
    assert row["apple_exercise_time"] == 25.0
    assert row["apple_exercise_time_goal"] == 30.0
    assert row["apple_stand_hours"] == 10.0
    assert row["apple_stand_hours_goal"] == 12.0

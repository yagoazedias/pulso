import xml.etree.ElementTree as ET

import pytest

from pulso.loader import workouts

pytestmark = pytest.mark.integration


def test_process_workout_all_children(test_ds, count_rows):
    batchers = workouts.make_batchers(test_ds, 10)
    element = ET.Element("Workout", {
        "workoutActivityType": "HKWorkoutActivityTypeRunning",
        "duration": "60.0", "durationUnit": "min",
        "totalDistance": "10.5", "totalDistanceUnit": "km",
        "totalEnergyBurned": "500.0", "totalEnergyBurnedUnit": "kcal",
        "sourceName": "iPhone", "sourceVersion": "15.0", "device": "iPhone (User's Device)",
        "creationDate": "2025-01-15 10:00:00 -0300",
        "startDate": "2025-01-15 09:00:00 -0300",
        "endDate": "2025-01-15 10:00:00 -0300",
    })
    element.append(ET.Element("MetadataEntry", {"key": "HKMetadataKeyWorkoutBrandName", "value": "Apple"}))
    element.append(ET.Element("WorkoutEvent", {
        "type": "HKWorkoutEventTypePause", "date": "2025-01-15 09:30:00 -0300",
        "duration": "5.0", "durationUnit": "min",
    }))
    element.append(ET.Element("WorkoutStatistics", {
        "type": "HKQuantityTypeIdentifierHeartRateVariabilitySDNN",
        "startDate": "2025-01-15 09:00:00 -0300", "endDate": "2025-01-15 10:00:00 -0300",
        "average": "45.0", "minimum": "30.0", "maximum": "60.0", "sum": "45.0", "unit": "ms",
    }))
    route = ET.Element("WorkoutRoute", {
        "sourceName": "Apple Watch",
        "startDate": "2025-01-15 09:00:00 -0300", "endDate": "2025-01-15 10:00:00 -0300",
    })
    route.append(ET.Element("FileReference", {"path": "/path/to/route.gpx"}))
    element.append(route)

    workouts.process(test_ds, batchers, element)
    workouts.flush(batchers)

    assert count_rows("workout") == 1
    assert count_rows("workout_metadata") == 1
    assert count_rows("workout_event") == 1
    assert count_rows("workout_statistics") == 1
    assert count_rows("workout_route") == 1


def test_process_workout_no_children(test_ds, count_rows):
    batchers = workouts.make_batchers(test_ds, 10)
    element = ET.Element("Workout", {
        "workoutActivityType": "HKWorkoutActivityTypeCycling",
        "duration": "30.0", "durationUnit": "min",
        "sourceName": "iPhone", "sourceVersion": "15.0", "device": "iPhone (User's Device)",
        "creationDate": "2025-01-15 10:00:00 -0300",
        "startDate": "2025-01-15 09:00:00 -0300",
        "endDate": "2025-01-15 09:30:00 -0300",
    })

    workouts.process(test_ds, batchers, element)
    workouts.flush(batchers)

    assert count_rows("workout") == 1
    assert count_rows("workout_metadata") == 0
    assert count_rows("workout_event") == 0
    assert count_rows("workout_statistics") == 0
    assert count_rows("workout_route") == 0

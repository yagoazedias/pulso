import xml.etree.ElementTree as ET
from datetime import date, timedelta, timezone

from pulso.xml import transform as t


def _el(tag, attrib, children=None):
    element = ET.Element(tag, attrib)
    for child in children or []:
        element.append(child)
    return element


# --- parse_datetime ---

def test_parse_datetime_valid():
    result = t.parse_datetime("2025-01-01 10:00:00 -0300")
    assert result.year == 2025
    assert result.month == 1
    assert result.day == 1
    assert result.hour == 10
    assert result.minute == 0
    assert result.utcoffset() == timedelta(hours=-3)


def test_parse_datetime_nil_and_empty():
    assert t.parse_datetime(None) is None
    assert t.parse_datetime("") is None


# --- parse_date ---

def test_parse_date_valid():
    result = t.parse_date("1998-10-11")
    assert result == date(1998, 10, 11)


def test_parse_date_nil_and_empty():
    assert t.parse_date(None) is None
    assert t.parse_date("") is None


# --- parse_dbl ---

def test_parse_dbl_valid():
    assert t.parse_dbl("72.5") == 72.5
    assert t.parse_dbl("0") == 0.0
    assert t.parse_dbl("-3.14") == -3.14


def test_parse_dbl_invalid():
    assert t.parse_dbl("abc") is None
    assert t.parse_dbl(None) is None
    assert t.parse_dbl("") is None


# --- export_date_element_to_map ---

def test_export_date_element_to_map():
    element = _el("ExportDate", {"value": "2025-06-15 08:30:00 -0300"})
    result = t.export_date_element_to_map(element)
    assert result["export_date"].year == 2025
    assert result["export_date"].month == 6


# --- me_element_to_map ---

def test_me_element_to_map():
    element = _el("Me", {
        "HKCharacteristicTypeIdentifierDateOfBirth": "1998-10-11",
        "HKCharacteristicTypeIdentifierBiologicalSex": "HKBiologicalSexMale",
        "HKCharacteristicTypeIdentifierBloodType": "HKBloodTypeAPositive",
        "HKCharacteristicTypeIdentifierFitzpatrickSkinType": "HKFitzpatrickSkinTypeIII",
        "HKCharacteristicTypeIdentifierCardioFitnessMedicationsUse": "HKCardioFitnessMedicationsUseNone",
    })
    result = t.me_element_to_map(element, "pt_BR")
    assert result["date_of_birth"] == date(1998, 10, 11)
    assert result["biological_sex"] == "HKBiologicalSexMale"
    assert result["blood_type"] == "HKBloodTypeAPositive"
    assert result["fitzpatrick_skin"] == "HKFitzpatrickSkinTypeIII"
    assert result["cardio_fitness_meds"] == "HKCardioFitnessMedicationsUseNone"
    assert result["locale"] == "pt_BR"


# --- record_element_to_map ---

def test_record_element_to_map_without_metadata():
    element = _el("Record", {
        "type": "HKQuantityTypeIdentifierActiveEnergyBurned",
        "sourceName": "Apple Watch",
        "sourceVersion": "10.0",
        "unit": "kcal",
        "value": "15.3",
        "device": "<<HKDevice>>",
        "creationDate": "2025-01-15 11:00:00 -0300",
        "startDate": "2025-01-15 11:00:00 -0300",
        "endDate": "2025-01-15 11:15:00 -0300",
    })
    result = t.record_element_to_map(element)
    assert result["type"] == "HKQuantityTypeIdentifierActiveEnergyBurned"
    assert result["source_name"] == "Apple Watch"
    assert result["source_version"] == "10.0"
    assert result["unit"] == "kcal"
    assert result["value"] == "15.3"
    assert result["creation_date"] is not None
    assert result["start_date"] is not None
    assert result["end_date"] is not None
    assert result["metadata"] == []


def test_record_element_to_map_with_metadata():
    element = _el("Record", {
        "type": "HKQuantityTypeIdentifierHeartRate",
        "sourceName": "Apple Watch",
        "sourceVersion": "10.0",
        "unit": "count/min",
        "value": "72",
        "device": "<<HKDevice>>",
        "creationDate": "2025-01-15 10:30:00 -0300",
        "startDate": "2025-01-15 10:30:00 -0300",
        "endDate": "2025-01-15 10:30:00 -0300",
    }, children=[
        _el("MetadataEntry", {"key": "HKMetadataKeyHeartRateMotionContext", "value": "1"}),
        _el("MetadataEntry", {"key": "HKMetadataKeyHeartRateSensorLocation", "value": "2"}),
    ])
    result = t.record_element_to_map(element)
    assert len(result["metadata"]) == 2
    assert result["metadata"][0]["key"] == "HKMetadataKeyHeartRateMotionContext"
    assert result["metadata"][0]["value"] == "1"
    assert result["metadata"][1]["key"] == "HKMetadataKeyHeartRateSensorLocation"


# --- workout_element_to_map ---

def test_workout_element_to_map_full():
    element = _el("Workout", {
        "workoutActivityType": "HKWorkoutActivityTypeRunning",
        "duration": "30.5",
        "durationUnit": "min",
        "totalDistance": "5.2",
        "totalDistanceUnit": "km",
        "totalEnergyBurned": "350.7",
        "totalEnergyBurnedUnit": "kcal",
        "sourceName": "Apple Watch",
        "sourceVersion": "10.0",
        "device": "<<HKDevice>>",
        "creationDate": "2025-01-15 07:00:00 -0300",
        "startDate": "2025-01-15 07:00:00 -0300",
        "endDate": "2025-01-15 07:30:30 -0300",
    }, children=[
        _el("MetadataEntry", {"key": "HKIndoorWorkout", "value": "0"}),
        _el("WorkoutEvent", {
            "type": "HKWorkoutEventTypePause",
            "date": "2025-01-15 07:15:00 -0300",
            "duration": "1.5",
            "durationUnit": "min",
        }),
        _el("WorkoutStatistics", {
            "type": "HKQuantityTypeIdentifierHeartRate",
            "startDate": "2025-01-15 07:00:00 -0300",
            "endDate": "2025-01-15 07:30:30 -0300",
            "average": "155.2",
            "minimum": "120.0",
            "maximum": "185.5",
            "sum": "",
            "unit": "count/min",
        }),
        _el("WorkoutRoute", {
            "sourceName": "Apple Watch",
            "startDate": "2025-01-15 07:00:00 -0300",
            "endDate": "2025-01-15 07:30:30 -0300",
        }, children=[
            _el("FileReference", {"path": "/workout-routes/route.gpx"}),
        ]),
    ])
    result = t.workout_element_to_map(element)
    assert result["activity_type"] == "HKWorkoutActivityTypeRunning"
    assert result["duration"] == 30.5
    assert result["duration_unit"] == "min"
    assert result["total_distance"] == 5.2
    assert result["total_distance_unit"] == "km"
    assert result["total_energy_burned"] == 350.7
    assert result["total_energy_burned_unit"] == "kcal"
    assert result["creation_date"] is not None
    assert len(result["metadata"]) == 1
    assert result["metadata"][0]["key"] == "HKIndoorWorkout"
    assert len(result["events"]) == 1
    assert result["events"][0]["type"] == "HKWorkoutEventTypePause"
    assert result["events"][0]["duration"] == 1.5
    assert len(result["statistics"]) == 1
    stat = result["statistics"][0]
    assert stat["average"] == 155.2
    assert stat["minimum"] == 120.0
    assert stat["maximum"] == 185.5
    assert stat["sum"] is None  # empty string -> None
    assert stat["unit"] == "count/min"
    assert len(result["routes"]) == 1
    assert result["routes"][0]["file_path"] == "/workout-routes/route.gpx"


def test_workout_element_to_map_empty_children():
    element = _el("Workout", {
        "workoutActivityType": "HKWorkoutActivityTypeYoga",
        "duration": "60.0",
        "durationUnit": "min",
        "sourceName": "iPhone",
        "creationDate": "2025-01-15 18:00:00 -0300",
        "startDate": "2025-01-15 18:00:00 -0300",
        "endDate": "2025-01-15 19:00:00 -0300",
    })
    result = t.workout_element_to_map(element)
    assert result["activity_type"] == "HKWorkoutActivityTypeYoga"
    assert result["metadata"] == []
    assert result["events"] == []
    assert result["statistics"] == []
    assert result["routes"] == []


# --- correlation_element_to_map ---

def test_correlation_element_to_map():
    element = _el("Correlation", {
        "type": "HKCorrelationTypeIdentifierBloodPressure",
        "sourceName": "Omron",
        "sourceVersion": "3.0",
        "device": "<<HKDevice>>",
        "creationDate": "2025-01-15 08:00:00 -0300",
        "startDate": "2025-01-15 08:00:00 -0300",
        "endDate": "2025-01-15 08:00:00 -0300",
    }, children=[
        _el("MetadataEntry", {"key": "HKMetadataKeyWasUserEntered", "value": "1"}),
        _el("Record", {
            "type": "HKQuantityTypeIdentifierBloodPressureSystolic",
            "sourceName": "Omron", "sourceVersion": "3.0", "unit": "mmHg", "value": "120",
            "device": "<<HKDevice>>",
            "creationDate": "2025-01-15 08:00:00 -0300",
            "startDate": "2025-01-15 08:00:00 -0300",
            "endDate": "2025-01-15 08:00:00 -0300",
        }),
        _el("Record", {
            "type": "HKQuantityTypeIdentifierBloodPressureDiastolic",
            "sourceName": "Omron", "sourceVersion": "3.0", "unit": "mmHg", "value": "80",
            "device": "<<HKDevice>>",
            "creationDate": "2025-01-15 08:00:00 -0300",
            "startDate": "2025-01-15 08:00:00 -0300",
            "endDate": "2025-01-15 08:00:00 -0300",
        }),
    ])
    result = t.correlation_element_to_map(element)
    assert result["type"] == "HKCorrelationTypeIdentifierBloodPressure"
    assert result["source_name"] == "Omron"
    assert len(result["metadata"]) == 1
    assert len(result["records"]) == 2
    assert result["records"][0]["value"] == "120"
    assert result["records"][1]["value"] == "80"


# --- activity_summary_element_to_map ---

def test_activity_summary_element_to_map():
    element = _el("ActivitySummary", {
        "dateComponents": "2025-01-15",
        "activeEnergyBurned": "450.5",
        "activeEnergyBurnedGoal": "500",
        "activeEnergyBurnedUnit": "kcal",
        "appleMoveTime": "35.2",
        "appleMoveTimeGoal": "30",
        "appleExerciseTime": "42.0",
        "appleExerciseTimeGoal": "30",
        "appleStandHours": "10",
        "appleStandHoursGoal": "12",
    })
    result = t.activity_summary_element_to_map(element)
    assert result["date_components"] == date(2025, 1, 15)
    assert result["active_energy_burned"] == 450.5
    assert result["active_energy_burned_goal"] == 500.0
    assert result["active_energy_burned_unit"] == "kcal"
    assert result["apple_move_time"] == 35.2
    assert result["apple_move_time_goal"] == 30.0
    assert result["apple_exercise_time"] == 42.0
    assert result["apple_exercise_time_goal"] == 30.0
    assert result["apple_stand_hours"] == 10.0
    assert result["apple_stand_hours_goal"] == 12.0


def test_activity_summary_partial():
    element = _el("ActivitySummary", {
        "dateComponents": "2025-01-15",
        "activeEnergyBurned": "100",
        "activeEnergyBurnedUnit": "kcal",
    })
    result = t.activity_summary_element_to_map(element)
    assert result["date_components"] == date(2025, 1, 15)
    assert result["active_energy_burned"] == 100.0
    assert result["active_energy_burned_goal"] is None
    assert result["apple_move_time"] is None
    assert result["apple_exercise_time"] is None
    assert result["apple_stand_hours"] is None

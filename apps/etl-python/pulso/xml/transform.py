from datetime import datetime, date

DATETIME_FORMAT = "%Y-%m-%d %H:%M:%S %z"


def parse_datetime(s):
    if not s:
        return None
    return datetime.strptime(s, DATETIME_FORMAT)


def parse_date(s):
    if not s:
        return None
    return date.fromisoformat(s)


def parse_dbl(s):
    if not s:
        return None
    try:
        return float(s)
    except ValueError:
        return None


def _extract_metadata(element):
    return [
        {"key": e.attrib.get("key"), "value": e.attrib.get("value")}
        for e in element.findall("MetadataEntry")
    ]


def export_date_element_to_map(element):
    return {"export_date": parse_datetime(element.attrib.get("value"))}


def me_element_to_map(element, locale):
    a = element.attrib
    return {
        "date_of_birth": parse_date(a.get("HKCharacteristicTypeIdentifierDateOfBirth")),
        "biological_sex": a.get("HKCharacteristicTypeIdentifierBiologicalSex"),
        "blood_type": a.get("HKCharacteristicTypeIdentifierBloodType"),
        "fitzpatrick_skin": a.get("HKCharacteristicTypeIdentifierFitzpatrickSkinType"),
        "cardio_fitness_meds": a.get("HKCharacteristicTypeIdentifierCardioFitnessMedicationsUse"),
        "locale": locale,
    }


def record_element_to_map(element):
    a = element.attrib
    return {
        "type": a.get("type"),
        "source_name": a.get("sourceName"),
        "source_version": a.get("sourceVersion"),
        "unit": a.get("unit"),
        "value": a.get("value"),
        "device": a.get("device"),
        "creation_date": parse_datetime(a.get("creationDate")),
        "start_date": parse_datetime(a.get("startDate")),
        "end_date": parse_datetime(a.get("endDate")),
        "metadata": _extract_metadata(element),
    }


def workout_element_to_map(element):
    a = element.attrib

    events = [
        {
            "type": e.attrib.get("type"),
            "date": parse_datetime(e.attrib.get("date")),
            "duration": parse_dbl(e.attrib.get("duration")),
            "duration_unit": e.attrib.get("durationUnit"),
        }
        for e in element.findall("WorkoutEvent")
    ]

    statistics = [
        {
            "type": s.attrib.get("type"),
            "start_date": parse_datetime(s.attrib.get("startDate")),
            "end_date": parse_datetime(s.attrib.get("endDate")),
            "average": parse_dbl(s.attrib.get("average")),
            "minimum": parse_dbl(s.attrib.get("minimum")),
            "maximum": parse_dbl(s.attrib.get("maximum")),
            "sum": parse_dbl(s.attrib.get("sum")),
            "unit": s.attrib.get("unit"),
        }
        for s in element.findall("WorkoutStatistics")
    ]

    routes = []
    for r in element.findall("WorkoutRoute"):
        file_ref = r.find("FileReference")
        routes.append({
            "source_name": r.attrib.get("sourceName"),
            "start_date": parse_datetime(r.attrib.get("startDate")),
            "end_date": parse_datetime(r.attrib.get("endDate")),
            "file_path": file_ref.attrib.get("path") if file_ref is not None else None,
        })

    return {
        "activity_type": a.get("workoutActivityType"),
        "duration": parse_dbl(a.get("duration")),
        "duration_unit": a.get("durationUnit"),
        "total_distance": parse_dbl(a.get("totalDistance")),
        "total_distance_unit": a.get("totalDistanceUnit"),
        "total_energy_burned": parse_dbl(a.get("totalEnergyBurned")),
        "total_energy_burned_unit": a.get("totalEnergyBurnedUnit"),
        "source_name": a.get("sourceName"),
        "source_version": a.get("sourceVersion"),
        "device": a.get("device"),
        "creation_date": parse_datetime(a.get("creationDate")),
        "start_date": parse_datetime(a.get("startDate")),
        "end_date": parse_datetime(a.get("endDate")),
        "metadata": _extract_metadata(element),
        "events": events,
        "statistics": statistics,
        "routes": routes,
    }


def correlation_element_to_map(element):
    a = element.attrib
    return {
        "type": a.get("type"),
        "source_name": a.get("sourceName"),
        "source_version": a.get("sourceVersion"),
        "device": a.get("device"),
        "creation_date": parse_datetime(a.get("creationDate")),
        "start_date": parse_datetime(a.get("startDate")),
        "end_date": parse_datetime(a.get("endDate")),
        "metadata": _extract_metadata(element),
        "records": [record_element_to_map(r) for r in element.findall("Record")],
    }


def activity_summary_element_to_map(element):
    a = element.attrib
    return {
        "date_components": parse_date(a.get("dateComponents")),
        "active_energy_burned": parse_dbl(a.get("activeEnergyBurned")),
        "active_energy_burned_goal": parse_dbl(a.get("activeEnergyBurnedGoal")),
        "active_energy_burned_unit": a.get("activeEnergyBurnedUnit"),
        "apple_move_time": parse_dbl(a.get("appleMoveTime")),
        "apple_move_time_goal": parse_dbl(a.get("appleMoveTimeGoal")),
        "apple_exercise_time": parse_dbl(a.get("appleExerciseTime")),
        "apple_exercise_time_goal": parse_dbl(a.get("appleExerciseTimeGoal")),
        "apple_stand_hours": parse_dbl(a.get("appleStandHours")),
        "apple_stand_hours_goal": parse_dbl(a.get("appleStandHoursGoal")),
    }

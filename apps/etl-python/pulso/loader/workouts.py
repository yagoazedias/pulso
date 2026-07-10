from pulso.loader import lookups
from pulso.loader.batch import Batcher, ReturningBatcher
from pulso.xml import transform

WORKOUT_COLUMNS = [
    "activity_type", "duration", "duration_unit",
    "total_distance", "total_distance_unit",
    "total_energy_burned", "total_energy_burned_unit",
    "source_id", "device_id",
    "creation_date", "start_date", "end_date",
]


def make_batchers(pool, batch_size):
    return {
        "workout": ReturningBatcher(pool, "workout", WORKOUT_COLUMNS, batch_size),
        "metadata": Batcher(pool, "workout_metadata", ["workout_id", "key", "value"], batch_size),
        "event": Batcher(
            pool, "workout_event",
            ["workout_id", "type", "date", "duration", "duration_unit"], batch_size,
        ),
        "statistics": Batcher(
            pool, "workout_statistics",
            ["workout_id", "type", "start_date", "end_date",
             "average", "minimum", "maximum", "sum", "unit"], batch_size,
        ),
        "route": Batcher(
            pool, "workout_route",
            ["workout_id", "source_name", "start_date", "end_date", "file_path"], batch_size,
        ),
    }


def _process_returned_children(batchers, pairs):
    if not pairs:
        return
    for pair in pairs:
        workout_id = pair["id"]
        children = pair["metadata"]
        for m in children["metadata_entries"]:
            batchers["metadata"].add((workout_id, m["key"], m["value"]))
        for e in children["events"]:
            batchers["event"].add((workout_id, e["type"], e["date"], e["duration"], e["duration_unit"]))
        for s in children["statistics"]:
            batchers["statistics"].add((
                workout_id, s["type"], s["start_date"], s["end_date"],
                s["average"], s["minimum"], s["maximum"], s["sum"], s["unit"],
            ))
        for r in children["routes"]:
            batchers["route"].add(
                (workout_id, r["source_name"], r["start_date"], r["end_date"], r["file_path"])
            )


def process(pool, batchers, element):
    data = transform.workout_element_to_map(element)
    source_id = lookups.ensure_source_id(pool, data["source_name"], data["source_version"])
    device_id = lookups.ensure_device_id(pool, data["device"])
    row = (
        data["activity_type"], data["duration"], data["duration_unit"],
        data["total_distance"], data["total_distance_unit"],
        data["total_energy_burned"], data["total_energy_burned_unit"],
        source_id, device_id,
        data["creation_date"], data["start_date"], data["end_date"],
    )
    children = {
        "metadata_entries": data["metadata"],
        "events": data["events"],
        "statistics": data["statistics"],
        "routes": data["routes"],
    }
    pairs = batchers["workout"].add(row, children)
    _process_returned_children(batchers, pairs)


def flush(batchers):
    pairs = batchers["workout"].flush()
    _process_returned_children(batchers, pairs)
    batchers["metadata"].flush()
    batchers["event"].flush()
    batchers["statistics"].flush()
    batchers["route"].flush()

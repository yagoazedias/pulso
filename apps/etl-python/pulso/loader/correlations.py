from pulso.loader import lookups
from pulso.loader.batch import Batcher, ReturningBatcher
from pulso.xml import transform

CORRELATION_COLUMNS = ["type", "source_id", "device_id", "creation_date", "start_date", "end_date"]
RECORD_COLUMNS = [
    "record_type_id", "source_id", "device_id", "unit_id",
    "value", "creation_date", "start_date", "end_date",
]


def make_batchers(pool, batch_size):
    """A dedicated nested-record ReturningBatcher avoids cross-batcher
    complexity with top-level records.py."""
    return {
        "correlation": ReturningBatcher(pool, "correlation", CORRELATION_COLUMNS, batch_size),
        "metadata": Batcher(pool, "correlation_metadata", ["correlation_id", "key", "value"], batch_size),
        "nested_record": ReturningBatcher(pool, "record", RECORD_COLUMNS, batch_size),
        "record_metadata": Batcher(pool, "record_metadata", ["record_id", "key", "value"], batch_size),
        "record_link": Batcher(pool, "correlation_record", ["correlation_id", "record_id"], batch_size),
    }


def _process_returned_nested_records(batchers, pairs):
    if not pairs:
        return
    for pair in pairs:
        record_id = pair["id"]
        meta = pair["metadata"]
        batchers["record_link"].add((meta["corr_id"], record_id))
        for m in meta["record_metadata"]:
            batchers["record_metadata"].add((record_id, m["key"], m["value"]))


def _process_returned_correlations(pool, batchers, pairs):
    if not pairs:
        return
    for pair in pairs:
        corr_id = pair["id"]
        meta = pair["metadata"]
        for m in meta["corr_metadata"]:
            batchers["metadata"].add((corr_id, m["key"], m["value"]))
        for record_map in meta["records"]:
            record_type_id = lookups.ensure_record_type_id(pool, record_map["type"])
            source_id = lookups.ensure_source_id(pool, record_map["source_name"], record_map["source_version"])
            device_id = lookups.ensure_device_id(pool, record_map["device"])
            unit_id = lookups.ensure_unit_id(pool, record_map["unit"])
            row = (
                record_type_id, source_id, device_id, unit_id,
                record_map["value"], record_map["creation_date"],
                record_map["start_date"], record_map["end_date"],
            )
            nested_pairs = batchers["nested_record"].add(
                row, {"corr_id": corr_id, "record_metadata": record_map["metadata"]}
            )
            _process_returned_nested_records(batchers, nested_pairs)


def process(pool, batchers, element):
    data = transform.correlation_element_to_map(element)
    source_id = lookups.ensure_source_id(pool, data["source_name"], data["source_version"])
    device_id = lookups.ensure_device_id(pool, data["device"])
    row = (data["type"], source_id, device_id, data["creation_date"], data["start_date"], data["end_date"])
    children = {"corr_metadata": data["metadata"], "records": data["records"]}
    pairs = batchers["correlation"].add(row, children)
    _process_returned_correlations(pool, batchers, pairs)


def flush(pool, batchers):
    """Three-phase flush: correlation parents -> nested records -> metadata + links."""
    corr_pairs = batchers["correlation"].flush()
    _process_returned_correlations(pool, batchers, corr_pairs)

    nested_pairs = batchers["nested_record"].flush()
    _process_returned_nested_records(batchers, nested_pairs)

    batchers["metadata"].flush()
    batchers["record_metadata"].flush()
    batchers["record_link"].flush()

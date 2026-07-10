from pulso.loader import lookups
from pulso.loader.batch import Batcher, ReturningBatcher
from pulso.xml import transform

RECORD_COLUMNS = [
    "record_type_id", "source_id", "device_id", "unit_id",
    "value", "creation_date", "start_date", "end_date",
]


def make_batchers(pool, batch_size):
    return {
        "record": Batcher(pool, "record", RECORD_COLUMNS, batch_size),
        "record_with_meta": ReturningBatcher(pool, "record", RECORD_COLUMNS, batch_size),
        "metadata": Batcher(pool, "record_metadata", ["record_id", "key", "value"], batch_size),
    }


def _process_returned_metadata(metadata_batcher, pairs):
    if not pairs:
        return
    for pair in pairs:
        for m in pair["metadata"]:
            metadata_batcher.add((pair["id"], m["key"], m["value"]))


def process(pool, batchers, element):
    """Transforms a Record element and inserts it. Records with metadata
    use a ReturningBatcher so generated ids can be matched with their
    metadata; records without metadata use a plain batch insert."""
    data = transform.record_element_to_map(element)
    record_type_id = lookups.ensure_record_type_id(pool, data["type"])
    source_id = lookups.ensure_source_id(pool, data["source_name"], data["source_version"])
    device_id = lookups.ensure_device_id(pool, data["device"])
    unit_id = lookups.ensure_unit_id(pool, data["unit"])
    row = (
        record_type_id, source_id, device_id, unit_id,
        data["value"], data["creation_date"], data["start_date"], data["end_date"],
    )

    if data["metadata"]:
        pairs = batchers["record_with_meta"].add(row, data["metadata"])
        _process_returned_metadata(batchers["metadata"], pairs)
    else:
        batchers["record"].add(row)

    return data


def flush(batchers):
    """Flushes the returning-batcher first so record ids are available for
    their metadata, then the plain batchers."""
    pairs = batchers["record_with_meta"].flush()
    _process_returned_metadata(batchers["metadata"], pairs)
    batchers["record"].flush()
    batchers["metadata"].flush()

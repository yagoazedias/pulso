import xml.etree.ElementTree as ET

import pytest

from pulso.loader import records

pytestmark = pytest.mark.integration


def test_process_record_without_metadata(test_ds, count_rows):
    batchers = records.make_batchers(test_ds, 10)
    element = ET.Element("Record", {
        "type": "HKQuantityTypeIdentifierStepCount",
        "sourceName": "iPhone",
        "sourceVersion": "15.0",
        "value": "1234",
        "unit": "count",
        "creationDate": "2025-01-15 10:00:00 -0300",
        "startDate": "2025-01-15 09:00:00 -0300",
        "endDate": "2025-01-15 10:00:00 -0300",
    })

    records.process(test_ds, batchers, element)

    assert count_rows("record") == 0
    assert batchers["record"].count() == 1


def test_process_record_with_metadata(test_ds, count_rows):
    batchers = records.make_batchers(test_ds, 10)
    element = ET.Element("Record", {
        "type": "HKQuantityTypeIdentifierHeartRate",
        "sourceName": "Apple Watch",
        "sourceVersion": "8.0",
        "value": "72",
        "unit": "count/min",
        "creationDate": "2025-01-15 10:00:00 -0300",
        "startDate": "2025-01-15 09:00:00 -0300",
        "endDate": "2025-01-15 10:00:00 -0300",
    })
    element.append(ET.Element("MetadataEntry", {"key": "HKMetadataKeyHeartRateMotionContext", "value": "0"}))
    element.append(ET.Element("MetadataEntry", {"key": "HKMetadataKeyExternalUUID", "value": "abc-123"}))

    records.process(test_ds, batchers, element)

    assert count_rows("record") == 0
    assert batchers["record_with_meta"].count() == 1

    records.flush(batchers)

    assert count_rows("record") == 1
    assert count_rows("record_metadata") == 2


def test_flush_clears_pending(test_ds, count_rows):
    batchers = records.make_batchers(test_ds, 10)

    records.process(test_ds, batchers, ET.Element("Record", {
        "type": "HKQuantityTypeIdentifierStepCount",
        "sourceName": "iPhone", "sourceVersion": "15.0", "value": "1000", "unit": "count",
        "creationDate": "2025-01-15 10:00:00 -0300",
        "startDate": "2025-01-15 09:00:00 -0300",
        "endDate": "2025-01-15 10:00:00 -0300",
    }))
    records.process(test_ds, batchers, ET.Element("Record", {
        "type": "HKQuantityTypeIdentifierActiveEnergyBurned",
        "sourceName": "iPhone", "sourceVersion": "15.0", "value": "100.5", "unit": "kcal",
        "creationDate": "2025-01-15 10:00:00 -0300",
        "startDate": "2025-01-15 09:00:00 -0300",
        "endDate": "2025-01-15 10:00:00 -0300",
    }))

    records.flush(batchers)

    assert count_rows("record") == 2
    assert batchers["record"].count() == 2

import xml.etree.ElementTree as ET

import pytest

from pulso.loader import correlations

pytestmark = pytest.mark.integration


def test_process_correlation_with_nested_records(test_ds, count_rows):
    batchers = correlations.make_batchers(test_ds, 10)
    element = ET.Element("Correlation", {
        "type": "HKCorrelationTypeIdentifierBloodPressure",
        "sourceName": "iPhone", "sourceVersion": "15.0",
        "creationDate": "2025-01-15 10:00:00 -0300",
        "startDate": "2025-01-15 09:00:00 -0300",
        "endDate": "2025-01-15 10:00:00 -0300",
    })
    element.append(ET.Element("MetadataEntry", {"key": "HKMetadataKeyGroupName", "value": "TestGroup"}))

    systolic = ET.Element("Record", {
        "type": "HKQuantityTypeIdentifierBloodPressureSystolic",
        "sourceName": "iPhone", "sourceVersion": "15.0", "value": "120", "unit": "mmHg",
        "creationDate": "2025-01-15 10:00:00 -0300",
        "startDate": "2025-01-15 09:00:00 -0300",
        "endDate": "2025-01-15 10:00:00 -0300",
    })
    systolic.append(ET.Element("MetadataEntry", {"key": "HKMetadataKeySourceSecondaryID", "value": "source-1"}))
    element.append(systolic)

    diastolic = ET.Element("Record", {
        "type": "HKQuantityTypeIdentifierBloodPressureDiastolic",
        "sourceName": "iPhone", "sourceVersion": "15.0", "value": "80", "unit": "mmHg",
        "creationDate": "2025-01-15 10:00:00 -0300",
        "startDate": "2025-01-15 09:00:00 -0300",
        "endDate": "2025-01-15 10:00:00 -0300",
    })
    diastolic.append(ET.Element("MetadataEntry", {"key": "HKMetadataKeySourceSecondaryID", "value": "source-2"}))
    element.append(diastolic)

    correlations.process(test_ds, batchers, element)
    correlations.flush(test_ds, batchers)

    assert count_rows("correlation") == 1
    assert count_rows("record") == 2
    assert count_rows("correlation_record") == 2
    assert count_rows("correlation_metadata") == 1
    assert count_rows("record_metadata") == 2

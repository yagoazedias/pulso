import os
import xml.etree.ElementTree as ET

from pulso.xml import parser

FIXTURE = os.path.join(os.path.dirname(__file__), "..", "..", "fixtures", "small-export.xml")


def test_parse_health_data_dispatches_all_elements():
    collected = []
    locale_seen = []

    def handler(element, locale):
        locale_seen.append(locale)
        collected.append(element.tag)

    parser.parse_health_data(FIXTURE, handler)

    assert locale_seen[-1] == "pt_BR"
    assert collected == ["ExportDate", "Me", "Record", "Record", "Workout", "Correlation", "ActivitySummary"]


def test_parse_health_data_with_doctype(tmp_path):
    xml_content = """<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE HealthData [
  <!ATTLIST ExportDate value CDATA #REQUIRED>
  <!ATTLIST Record type CDATA #REQUIRED>
]>
<HealthData locale="en_US">
 <ExportDate value="2025-01-01 00:00:00 +0000"/>
 <Record type="HKQuantityTypeIdentifierStepCount"
         sourceName="iPhone"
         value="100"
         creationDate="2025-01-01 12:00:00 +0000"
         startDate="2025-01-01 12:00:00 +0000"
         endDate="2025-01-01 12:30:00 +0000"/>
</HealthData>"""
    xml_path = tmp_path / "health-doctype.xml"
    xml_path.write_text(xml_content)

    tags = []
    parser.parse_health_data(str(xml_path), lambda element, locale: tags.append(element.tag))

    assert tags == ["ExportDate", "Record"]


def test_parse_health_data_skips_whitespace():
    non_element_count = 0

    def handler(element, locale):
        nonlocal non_element_count
        if not isinstance(element, ET.Element):
            non_element_count += 1

    parser.parse_health_data(FIXTURE, handler)

    assert non_element_count == 0

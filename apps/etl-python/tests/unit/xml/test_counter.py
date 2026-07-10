import os

from pulso.xml import counter

FIXTURE = os.path.join(os.path.dirname(__file__), "..", "..", "fixtures", "small-export.xml")


def test_count_elements_fixture():
    counts = counter.count_elements(FIXTURE)

    assert counts["Record"] == 2
    assert counts["Workout"] == 1
    assert counts["Correlation"] == 1
    assert counts["ActivitySummary"] == 1
    assert counts["ExportDate"] == 1
    assert counts["Me"] == 1

    assert "MetadataEntry" not in counts
    assert "WorkoutEvent" not in counts
    assert "WorkoutStatistics" not in counts


def test_count_elements_with_doctype(tmp_path):
    xml_content = """<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE HealthData [
  <!ATTLIST ExportDate value CDATA #REQUIRED>
]>
<HealthData locale="en_US">
 <ExportDate value="2025-01-01 00:00:00 +0000"/>
 <Record type="StepCount" value="100"
         creationDate="2025-01-01" startDate="2025-01-01" endDate="2025-01-01"/>
 <Record type="HeartRate" value="72"
         creationDate="2025-01-01" startDate="2025-01-01" endDate="2025-01-01"/>
 <Record type="Distance" value="1.5"
         creationDate="2025-01-01" startDate="2025-01-01" endDate="2025-01-01"/>
</HealthData>"""
    xml_path = tmp_path / "health-count.xml"
    xml_path.write_text(xml_content)

    counts = counter.count_elements(str(xml_path))

    assert counts["Record"] == 3
    assert counts["ExportDate"] == 1

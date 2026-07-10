import xml.etree.ElementTree as ET
from datetime import timezone

import pytest

from pulso.loader import profile

pytestmark = pytest.mark.integration


def test_save_export_date_stores_value(test_ds, count_rows, select_all):
    profile.reset_state()
    export_element = ET.Element("ExportDate", {"value": "2025-01-15 12:00:00 -0300"})

    profile.save_export_date(export_element)
    me_element = ET.Element("Me", {
        "HKCharacteristicTypeIdentifierDateOfBirth": "1990-05-15",
        "HKCharacteristicTypeIdentifierBiologicalSex": "HKBiologicalSexMale",
    })
    profile.save_profile(test_ds, me_element, "pt_BR")

    assert count_rows("user_profile") == 1
    row = select_all("user_profile")[0]
    export_date_utc = row["export_date"].astimezone(timezone.utc)
    assert export_date_utc.year == 2025
    assert export_date_utc.month == 1
    assert export_date_utc.day == 15


def test_save_profile_inserts_row(test_ds, count_rows, select_all):
    profile.reset_state()
    export_element = ET.Element("ExportDate", {"value": "2025-01-15 12:00:00 -0300"})
    me_element = ET.Element("Me", {
        "HKCharacteristicTypeIdentifierDateOfBirth": "1990-05-15",
        "HKCharacteristicTypeIdentifierBiologicalSex": "HKBiologicalSexMale",
    })

    profile.save_export_date(export_element)
    profile.save_profile(test_ds, me_element, "pt_BR")

    assert count_rows("user_profile") == 1
    row = select_all("user_profile")[0]
    assert str(row["date_of_birth"]) == "1990-05-15"
    assert row["biological_sex"] == "HKBiologicalSexMale"
    assert row["locale"] == "pt_BR"

import pytest

from pulso.loader import lookups

pytestmark = pytest.mark.integration


def test_ensure_source_id_creates_and_caches(test_ds, count_rows):
    id1 = lookups.ensure_source_id(test_ds, "iPhone", "15.0")
    id2 = lookups.ensure_source_id(test_ds, "iPhone", "15.0")

    assert id1 == id2
    assert count_rows("source") == 1


def test_ensure_source_id_different_versions(test_ds, count_rows):
    id1 = lookups.ensure_source_id(test_ds, "iPhone", "15.0")
    id2 = lookups.ensure_source_id(test_ds, "iPhone", "16.0")

    assert id1 != id2
    assert count_rows("source") == 2


def test_ensure_source_id_nil_returns_nil(test_ds, count_rows):
    result = lookups.ensure_source_id(test_ds, None, "15.0")

    assert result is None
    assert count_rows("source") == 0


def test_ensure_device_id_creates_and_caches(test_ds, count_rows):
    id1 = lookups.ensure_device_id(test_ds, "iPhone (User's Device)")
    id2 = lookups.ensure_device_id(test_ds, "iPhone (User's Device)")

    assert id1 == id2
    assert count_rows("device") == 1


def test_ensure_unit_id_creates_and_caches(test_ds, count_rows):
    id1 = lookups.ensure_unit_id(test_ds, "count/min")
    id2 = lookups.ensure_unit_id(test_ds, "count/min")

    assert id1 == id2
    assert count_rows("unit") == 1

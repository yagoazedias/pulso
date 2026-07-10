from datetime import date

import pytest

from pulso.loader.batch import Batcher

pytestmark = pytest.mark.integration


def test_batcher_flushes_at_batch_size(test_ds, count_rows):
    batcher = Batcher(test_ds, "activity_summary", ["date_components", "active_energy_burned"], 2)

    batcher.add((date(2025, 1, 1), 100.0))
    batcher.add((date(2025, 1, 2), 200.0))
    batcher.add((date(2025, 1, 3), 300.0))

    assert count_rows("activity_summary") == 2
    assert batcher.count() == 3


def test_batcher_flush_partial(test_ds, count_rows):
    batcher = Batcher(test_ds, "activity_summary", ["date_components", "active_energy_burned"], 5)
    batcher.add((date(2025, 1, 1), 100.0))

    batcher.flush()

    assert count_rows("activity_summary") == 1
    assert batcher.count() == 1


def test_batcher_empty_flush_noop(test_ds, count_rows):
    batcher = Batcher(test_ds, "activity_summary", ["date_components", "active_energy_burned"], 5)

    batcher.flush()

    assert count_rows("activity_summary") == 0
    assert batcher.count() == 0

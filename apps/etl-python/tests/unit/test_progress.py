import time

from pulso import progress


def test_make_state_initializes_correctly():
    totals = {"Record": 1000, "Workout": 50, "Correlation": 20, "ActivitySummary": 365, "Me": 1}
    state = progress.make_state(totals)

    assert set(state["totals"].keys()) == {"Record", "Workout", "Correlation", "ActivitySummary"}
    assert "Me" not in state["totals"]
    assert all(v == 0 for v in state["processed"].values())


def test_record_progress_increments():
    state = progress.make_state({"Record": 100, "Workout": 10, "Correlation": 5, "ActivitySummary": 30})

    progress.record_progress(state, "Record")
    progress.record_progress(state, "Record")
    progress.record_progress(state, "Workout")

    assert state["processed"]["Record"] == 2
    assert state["processed"]["Workout"] == 1
    assert state["processed"]["Correlation"] == 0


def test_record_progress_ignores_untracked():
    state = progress.make_state({"Record": 100})

    progress.record_progress(state, "ExportDate")
    progress.record_progress(state, "Me")

    assert state["processed"]["Record"] == 0


def test_snapshot_calculates_percentages():
    state = {
        "totals": {"Record": 1000, "Workout": 50, "Correlation": 20, "ActivitySummary": 365},
        "processed": {"Record": 500, "Workout": 50, "Correlation": 10, "ActivitySummary": 0},
        "start_ms": time.time() * 1000 - 10000,
    }
    snap = progress.snapshot(state)

    record_info = next(x for x in snap["types"] if x["type"] == "Record")
    assert record_info["processed"] == 500
    assert record_info["total"] == 1000
    assert 49.9 < record_info["pct"] < 50.1

    workout_info = next(x for x in snap["types"] if x["type"] == "Workout")
    assert workout_info["pct"] == 100.0

    assert snap["overall_processed"] == 560
    assert snap["overall_total"] == 1435
    assert 38.0 < snap["overall_pct"] < 40.0

    assert snap["rate"] > 0
    assert snap["eta_secs"] > 0


def test_snapshot_handles_zero_totals():
    state = {
        "totals": {"Record": 0, "Workout": 0, "Correlation": 0, "ActivitySummary": 0},
        "processed": {"Record": 0, "Workout": 0, "Correlation": 0, "ActivitySummary": 0},
        "start_ms": time.time() * 1000,
    }
    snap = progress.snapshot(state)

    assert snap["overall_pct"] == 0.0
    assert snap["eta_secs"] == 0

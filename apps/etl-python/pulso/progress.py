import time

TRACKED_TYPES = ["Record", "Workout", "Correlation", "ActivitySummary"]


def make_state(totals):
    return {
        "totals": {t: totals[t] for t in TRACKED_TYPES if t in totals},
        "processed": {t: 0 for t in TRACKED_TYPES},
        "start_ms": time.time() * 1000,
    }


def record_progress(state, element_type):
    if element_type in state["processed"]:
        state["processed"][element_type] += 1


def snapshot(state):
    totals = state["totals"]
    processed = state["processed"]
    now_ms = time.time() * 1000
    elapsed_ms = max(1.0, now_ms - state["start_ms"])
    elapsed_s = elapsed_ms / 1000.0

    types = []
    for t in TRACKED_TYPES:
        proc = processed.get(t, 0)
        tot = totals.get(t, 0)
        pct = (100.0 * proc / tot) if tot > 0 else 0.0
        types.append({"type": t, "processed": proc, "total": tot, "pct": pct})

    overall_processed = sum(x["processed"] for x in types)
    overall_total = sum(x["total"] for x in types)
    overall_pct = (100.0 * overall_processed / overall_total) if overall_total > 0 else 0.0
    rate = int(overall_processed / elapsed_s) if elapsed_s > 0 else 0
    remaining = overall_total - overall_processed
    eta_secs = int(remaining / rate) if rate > 0 else 0

    return {
        "types": types,
        "overall_processed": overall_processed,
        "overall_total": overall_total,
        "overall_pct": overall_pct,
        "rate": rate,
        "eta_secs": eta_secs,
        "elapsed_secs": int(elapsed_s),
    }

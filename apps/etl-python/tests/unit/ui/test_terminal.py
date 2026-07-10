from pulso.ui import terminal


# --- format_number ---

def test_format_number():
    assert terminal.format_number(0) == "0"
    assert terminal.format_number(42) == "42"
    assert terminal.format_number(999) == "999"
    assert terminal.format_number(1000) == "1,000"
    assert terminal.format_number(1234567) == "1,234,567"
    assert terminal.format_number(10000000) == "10,000,000"


# --- format_duration ---

def test_format_duration():
    assert terminal.format_duration(0) == "0:00"
    assert terminal.format_duration(5) == "0:05"
    assert terminal.format_duration(59) == "0:59"
    assert terminal.format_duration(60) == "1:00"
    assert terminal.format_duration(65) == "1:05"
    assert terminal.format_duration(630) == "10:30"


# --- progress_bar ---

def test_progress_bar():
    assert terminal.progress_bar(0, 10) == "[░░░░░░░░░░]"
    assert terminal.progress_bar(100, 10) == "[██████████]"
    assert terminal.progress_bar(50, 10) == "[█████░░░░░]"
    assert terminal.progress_bar(150, 10) == "[██████████]"


# --- render_type_line ---

def test_render_type_line():
    line = terminal.render_type_line({"type": "Record", "processed": 500, "total": 1000, "pct": 50.0})
    assert "Record" in line
    assert "50.0%" in line
    assert "500" in line
    assert "1,000" in line


# --- render_frame ---

def test_render_frame():
    snap = {
        "types": [
            {"type": "Record", "processed": 500, "total": 1000, "pct": 50.0},
            {"type": "Workout", "processed": 10, "total": 20, "pct": 50.0},
        ],
        "overall_processed": 510,
        "overall_total": 1020,
        "overall_pct": 50.0,
        "rate": 100,
        "eta_secs": 5,
        "elapsed_secs": 65,
    }
    lines = terminal.render_frame(snap, "export.xml")

    assert isinstance(lines, list)
    assert all(isinstance(line, str) for line in lines)
    assert "export.xml" in lines[0]
    assert any("─" in line for line in lines)
    assert any("Record" in line for line in lines)
    assert any("Workout" in line for line in lines)
    summary = lines[-1]
    assert "50.0%" in summary
    assert "1:05" in summary

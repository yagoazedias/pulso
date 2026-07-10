import sys
import threading
import time

BAR_WIDTH = 30
RENDER_INTERVAL_S = 0.1


def format_number(n):
    return f"{int(n):,}"


def format_duration(secs):
    secs = int(secs)
    m, s = divmod(secs, 60)
    return f"{m}:{s:02d}"


def progress_bar(pct, width):
    filled = min(int((pct / 100.0) * width), width)
    empty = width - filled
    return "[" + ("█" * filled) + ("░" * empty) + "]"


def render_type_line(type_info):
    label = f"{type_info['type']:<15}"
    bar = progress_bar(type_info["pct"], BAR_WIDTH)
    pct_str = f"{type_info['pct']:5.1f}%"
    nums = f"{int(type_info['processed']):,} / {int(type_info['total']):,}"
    return f"  {label} {bar} {pct_str}  {nums}"


def render_frame(snap, filename):
    header = f"Pulso ETL - Processing {filename}"
    divider = "─" * 74
    type_lines = [render_type_line(t) for t in snap["types"]]
    summary = (
        f"  Overall: {snap['overall_pct']:5.1f}%  |  "
        f"{format_number(snap['overall_processed'])} / {format_number(snap['overall_total'])}  |  "
        f"{format_number(snap['rate'])}/s  |  ETA {snap['eta_secs']}s  |  "
        f"{format_duration(snap['elapsed_secs'])}"
    )
    return [header, divider, *type_lines, divider, summary]


def _move_cursor_up(n):
    return f"\033[{n}A"


def _clear_line():
    return "\033[2K\r"


def _hide_cursor():
    return "\033[?25l"


def _show_cursor():
    return "\033[?25h"


class Renderer:
    """Runs a daemon thread that redraws progress bars at ~10 FPS using
    cursor-up + overwrite for flicker-free in-place updates."""

    def __init__(self, state, snapshot_fn, filename):
        self._state = state
        self._snapshot_fn = snapshot_fn
        self._filename = filename
        self._running = threading.Event()
        self._running.set()
        self._lines_printed = 0
        self._thread = threading.Thread(
            target=self._run, daemon=True, name="pulso-progress-renderer"
        )
        self._thread.start()

    def _print_frame(self, lines):
        if self._lines_printed > 0:
            sys.stdout.write(_move_cursor_up(self._lines_printed))
        for line in lines:
            sys.stdout.write(_clear_line() + line + "\n")
        sys.stdout.flush()
        self._lines_printed = len(lines)

    def _run(self):
        sys.stdout.write(_hide_cursor())
        sys.stdout.flush()
        try:
            while self._running.is_set():
                snap = self._snapshot_fn(self._state)
                self._print_frame(render_frame(snap, self._filename))
                time.sleep(RENDER_INTERVAL_S)
        finally:
            sys.stdout.write(_show_cursor())
            sys.stdout.flush()

    def stop(self):
        self._running.clear()
        self._thread.join(timeout=0.5)
        snap = self._snapshot_fn(self._state)
        self._print_frame(render_frame(snap, self._filename))


def start_renderer(state, snapshot_fn, filename):
    return Renderer(state, snapshot_fn, filename)

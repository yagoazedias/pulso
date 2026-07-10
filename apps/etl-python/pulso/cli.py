import argparse
import logging
import os
import sys

from pulso import config, db, etl, progress
from pulso.ui import terminal
from pulso.xml import counter

logger = logging.getLogger(__name__)


def _build_arg_parser():
    parser = argparse.ArgumentParser(
        prog="pulso",
        description="Pulso - Apple Health XML to PostgreSQL ETL",
        add_help=False,
    )
    parser.add_argument("-f", "--file", help="Path to Apple Health XML export file")
    parser.add_argument(
        "-b", "--batch-size", type=int, default=config.DEFAULT_BATCH_SIZE, help="Batch insert size"
    )
    parser.add_argument(
        "-p", "--progress", dest="progress", action="store_true", default=True,
        help="Show progress bar (default: true)",
    )
    parser.add_argument("--no-progress", dest="progress", action="store_false")
    parser.add_argument("-h", "--help", dest="show_help", action="store_true", help="Show help")
    return parser


def _detach_console_handler():
    """Removes the root logger's stdout handler so log lines don't
    interfere with the progress UI, mirroring the Clojure app's
    detach-console-appender!."""
    root = logging.getLogger()
    for handler in list(root.handlers):
        if isinstance(handler, logging.StreamHandler) and handler.stream is sys.stdout:
            root.removeHandler(handler)
            return handler
    return None


def _reattach_console_handler(handler):
    if handler:
        logging.getLogger().addHandler(handler)


def _run_with_progress(pool, xml_file, batch_size):
    filename = os.path.basename(xml_file)
    print(f"Pulso ETL - Counting elements in {filename}...")
    totals = counter.count_elements(xml_file)
    state = progress.make_state(totals)
    detached = _detach_console_handler()
    renderer = terminal.start_renderer(state, progress.snapshot, filename)
    try:
        result = etl.execute(
            pool, xml_file, batch_size,
            on_element=lambda element_type: progress.record_progress(state, element_type),
        )
        renderer.stop()
        _reattach_console_handler(detached)
        print()
        logger.info("Final counts: %s", result)
        return result
    except Exception:
        renderer.stop()
        _reattach_console_handler(detached)
        raise


def main(argv=None):
    arg_parser = _build_arg_parser()
    args = arg_parser.parse_args(argv)

    if args.show_help:
        arg_parser.print_help()
        sys.exit(0)

    if not args.file:
        print("Error: --file is required")
        arg_parser.print_help()
        sys.exit(1)

    if not os.path.exists(args.file):
        print("Error: File does not exist")
        sys.exit(1)

    db_spec = config.db_spec()
    pool = db.create_pool(db_spec)
    logger.info(
        "Connecting to database host=%s port=%s dbname=%s",
        db_spec["host"], db_spec["port"], db_spec["dbname"],
    )
    db.migrate(pool)

    if args.progress:
        _run_with_progress(pool, args.file, args.batch_size)
    else:
        result = etl.execute(pool, args.file, args.batch_size)
        logger.info("Final counts: %s", result)

    sys.exit(0)


if __name__ == "__main__":
    main()

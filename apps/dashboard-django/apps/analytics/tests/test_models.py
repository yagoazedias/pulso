from apps.analytics.models import ActivitySummary, Workout, Record, RecordType


def test_activity_summary_is_unmanaged():
    assert ActivitySummary._meta.managed is False


def test_activity_summary_table_name():
    assert ActivitySummary._meta.db_table == '"public"."activity_summary"'


def test_workout_is_unmanaged():
    assert Workout._meta.managed is False


def test_workout_table_name():
    assert Workout._meta.db_table == '"public"."workout"'


def test_record_is_unmanaged():
    assert Record._meta.managed is False


def test_record_table_name():
    assert Record._meta.db_table == '"public"."record"'


def test_record_type_is_unmanaged():
    assert RecordType._meta.managed is False


def test_record_type_table_name():
    assert RecordType._meta.db_table == '"public"."record_type"'

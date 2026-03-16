from apps.analytics.models import ActivitySummary


def test_activity_summary_is_unmanaged():
    assert ActivitySummary._meta.managed is False


def test_activity_summary_table_name():
    assert ActivitySummary._meta.db_table == '"public"."activity_summary"'

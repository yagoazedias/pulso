from apps.analytics.repositories import PostgresMetricsRepository


def test_get_energy_vs_goal_returns_empty_list():
    repo = PostgresMetricsRepository()
    result = repo.get_energy_vs_goal()
    assert result == []


def test_get_energy_vs_goal_accepts_days_param():
    repo = PostgresMetricsRepository()
    result = repo.get_energy_vs_goal(days=30)
    assert result == []

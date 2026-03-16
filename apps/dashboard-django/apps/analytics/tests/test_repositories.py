import datetime

import pytest

from apps.analytics.repositories import PostgresMetricsRepository


@pytest.fixture
def repo():
    return PostgresMetricsRepository()


class TestGetEnergyVsGoal:
    @pytest.mark.django_db
    def test_returns_correct_structure(self, repo, energy_data):
        result = repo.get_energy_vs_goal(start="2026-03-10", end="2026-03-14")
        assert "labels" in result
        assert "datasets" in result
        assert "meta" in result
        assert len(result["datasets"]) == 2
        assert result["datasets"][0]["label"] == "Active Energy Burned"
        assert result["datasets"][1]["label"] == "Goal"

    @pytest.mark.django_db
    def test_returns_correct_values(self, repo, energy_data):
        result = repo.get_energy_vs_goal(start="2026-03-10", end="2026-03-14")
        assert result["labels"] == [
            "2026-03-10", "2026-03-11", "2026-03-12", "2026-03-13", "2026-03-14"
        ]
        assert result["datasets"][0]["data"] == [450, 520, 480, None, 600]
        assert result["datasets"][1]["data"] == [500, 500, 500, 500, 500]

    @pytest.mark.django_db
    def test_date_filtering(self, repo, energy_data):
        result = repo.get_energy_vs_goal(start="2026-03-11", end="2026-03-12")
        assert result["labels"] == ["2026-03-11", "2026-03-12"]
        assert result["datasets"][0]["data"] == [520, 480]

    @pytest.mark.django_db
    def test_empty_range_returns_empty_arrays(self, repo, energy_data):
        result = repo.get_energy_vs_goal(start="2025-01-01", end="2025-01-02")
        assert result["labels"] == []
        assert result["datasets"][0]["data"] == []
        assert result["datasets"][1]["data"] == []

    @pytest.mark.django_db
    def test_meta_fields(self, repo, energy_data):
        result = repo.get_energy_vs_goal(start="2026-03-10", end="2026-03-14")
        assert result["meta"]["unit"] == "kcal"
        assert result["meta"]["window"] == "custom"
        assert result["meta"]["last_updated"] == "2026-03-14"

    @pytest.mark.django_db
    def test_default_window_meta(self, repo, energy_data):
        result = repo.get_energy_vs_goal()
        assert result["meta"]["window"] == "90d"

    @pytest.mark.django_db
    def test_empty_result_last_updated_is_none(self, repo):
        result = repo.get_energy_vs_goal(start="2025-01-01", end="2025-01-02")
        assert result["meta"]["last_updated"] is None

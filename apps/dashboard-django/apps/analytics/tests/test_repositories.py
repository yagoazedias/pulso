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


class TestGetWorkoutVolume:
    @pytest.mark.django_db
    def test_returns_correct_structure(self, repo, workout_data):
        result = repo.get_workout_volume(start="2026-03-02", end="2026-03-15")
        assert "labels" in result
        assert "datasets" in result
        assert "meta" in result
        assert len(result["datasets"]) == 3
        assert result["datasets"][0]["label"] == "Workouts"
        assert result["datasets"][1]["label"] == "Duration (min)"
        assert result["datasets"][2]["label"] == "Energy Burned (kcal)"

    @pytest.mark.django_db
    def test_weekly_aggregation(self, repo, workout_data):
        result = repo.get_workout_volume(start="2026-03-02", end="2026-03-15")
        # Week 2026-03-02: 3 workouts, duration (1800+3600+0)/60=90 min, energy 300+500+0=800
        # Week 2026-03-09: 3 workouts, duration (2400+3600+1200)/60=120 min, energy 400+200+250=850
        assert result["datasets"][0]["data"] == [3, 3]
        assert result["datasets"][1]["data"] == [90.0, 120.0]
        assert result["datasets"][2]["data"] == [800, 850]

    @pytest.mark.django_db
    def test_labels_are_week_mondays(self, repo, workout_data):
        result = repo.get_workout_volume(start="2026-03-02", end="2026-03-15")
        assert result["labels"] == ["2026-03-02", "2026-03-09"]

    @pytest.mark.django_db
    def test_null_duration_energy_dont_break_sums(self, repo, workout_data):
        """The Swimming workout has null duration and energy — sums should still work."""
        result = repo.get_workout_volume(start="2026-03-02", end="2026-03-08")
        # Week has 3 workouts: Running (1800s, 300kcal), Cycling (3600s, 500kcal), Swimming (null, null)
        assert result["datasets"][0]["data"] == [3]
        assert result["datasets"][1]["data"] == [90.0]  # (1800+3600)/60, null excluded
        assert result["datasets"][2]["data"] == [800]  # 300+500, null excluded

    @pytest.mark.django_db
    def test_empty_range(self, repo, workout_data):
        result = repo.get_workout_volume(start="2025-01-01", end="2025-01-07")
        assert result["labels"] == []
        assert result["datasets"][0]["data"] == []

    @pytest.mark.django_db
    def test_meta_fields(self, repo, workout_data):
        result = repo.get_workout_volume(start="2026-03-02", end="2026-03-15")
        assert result["meta"]["unit"] == "mixed"
        assert result["meta"]["window"] == "custom"

    @pytest.mark.django_db
    def test_default_window_meta(self, repo, workout_data):
        result = repo.get_workout_volume()
        assert result["meta"]["window"] == "12w"


class TestGetTopRecordTypes:
    @pytest.mark.django_db
    def test_returns_correct_structure(self, repo, record_data):
        result = repo.get_top_record_types(start="2026-03-02", end="2026-03-15")
        assert "labels" in result
        assert "datasets" in result
        assert "meta" in result

    @pytest.mark.django_db
    def test_types_ordered_by_volume(self, repo, record_data):
        result = repo.get_top_record_types(start="2026-03-02", end="2026-03-15")
        # HeartRate: 9 total, StepCount: 5 total, Distance: 1 total
        assert result["datasets"][0]["label"] == "HKQuantityTypeIdentifierHeartRate"
        assert result["datasets"][1]["label"] == "HKQuantityTypeIdentifierStepCount"
        assert result["datasets"][2]["label"] == "HKQuantityTypeIdentifierDistanceWalkingRunning"

    @pytest.mark.django_db
    def test_weekly_bucketing(self, repo, record_data):
        result = repo.get_top_record_types(start="2026-03-02", end="2026-03-15")
        assert result["labels"] == ["2026-03-02", "2026-03-09"]
        # HeartRate: week1=5, week2=4
        assert result["datasets"][0]["data"] == [5, 4]
        # StepCount: week1=3, week2=2
        assert result["datasets"][1]["data"] == [3, 2]
        # Distance: week1=1, week2=0
        assert result["datasets"][2]["data"] == [1, 0]

    @pytest.mark.django_db
    def test_returns_max_5_types(self, repo, record_data):
        result = repo.get_top_record_types(start="2026-03-02", end="2026-03-15")
        assert len(result["datasets"]) <= 5

    @pytest.mark.django_db
    def test_fewer_than_5_types(self, repo, record_data):
        """Fixture only has 3 types — should return 3 datasets."""
        result = repo.get_top_record_types(start="2026-03-02", end="2026-03-15")
        assert len(result["datasets"]) == 3

    @pytest.mark.django_db
    def test_empty_range(self, repo, record_data):
        result = repo.get_top_record_types(start="2025-01-01", end="2025-01-07")
        assert result["labels"] == []
        assert result["datasets"] == []

    @pytest.mark.django_db
    def test_date_range_filtering(self, repo, record_data):
        result = repo.get_top_record_types(start="2026-03-02", end="2026-03-08")
        assert result["labels"] == ["2026-03-02"]

    @pytest.mark.django_db
    def test_meta_fields(self, repo, record_data):
        result = repo.get_top_record_types(start="2026-03-02", end="2026-03-15")
        assert result["meta"]["unit"] == "count"
        assert result["meta"]["window"] == "custom"

    @pytest.mark.django_db
    def test_default_window_meta(self, repo, record_data):
        result = repo.get_top_record_types()
        assert result["meta"]["window"] == "12w"

import json

import pytest
from django.test import Client


@pytest.fixture
def client():
    return Client()


class TestEnergyVsGoalAPI:
    @pytest.mark.django_db
    def test_returns_200(self, client, energy_data):
        response = client.get("/api/metrics/energy-vs-goal")
        assert response.status_code == 200

    @pytest.mark.django_db
    def test_returns_correct_json_structure(self, client, energy_data):
        response = client.get("/api/metrics/energy-vs-goal")
        data = json.loads(response.content)
        assert "labels" in data
        assert "datasets" in data
        assert "meta" in data
        assert len(data["datasets"]) == 2

    @pytest.mark.django_db
    def test_date_range_filtering(self, client, energy_data):
        response = client.get(
            "/api/metrics/energy-vs-goal?start=2026-03-11&end=2026-03-12"
        )
        data = json.loads(response.content)
        assert data["labels"] == ["2026-03-11", "2026-03-12"]

    @pytest.mark.django_db
    def test_empty_range_returns_empty_arrays(self, client, energy_data):
        response = client.get(
            "/api/metrics/energy-vs-goal?start=2025-01-01&end=2025-01-02"
        )
        data = json.loads(response.content)
        assert data["labels"] == []
        assert data["datasets"][0]["data"] == []

    @pytest.mark.django_db
    def test_invalid_date_returns_400(self, client):
        response = client.get("/api/metrics/energy-vs-goal?start=not-a-date")
        assert response.status_code == 400
        data = json.loads(response.content)
        assert "error" in data

    @pytest.mark.django_db
    def test_post_not_allowed(self, client):
        response = client.post("/api/metrics/energy-vs-goal")
        assert response.status_code == 405


class TestWorkoutVolumeAPI:
    @pytest.mark.django_db
    def test_returns_200(self, client, workout_data):
        response = client.get("/api/metrics/workout-volume")
        assert response.status_code == 200

    @pytest.mark.django_db
    def test_returns_3_datasets(self, client, workout_data):
        response = client.get("/api/metrics/workout-volume")
        data = json.loads(response.content)
        assert len(data["datasets"]) == 3

    @pytest.mark.django_db
    def test_date_range_filtering(self, client, workout_data):
        response = client.get(
            "/api/metrics/workout-volume?start=2026-03-02&end=2026-03-08"
        )
        data = json.loads(response.content)
        assert len(data["labels"]) == 1

    @pytest.mark.django_db
    def test_empty_range_returns_empty_arrays(self, client, workout_data):
        response = client.get(
            "/api/metrics/workout-volume?start=2025-01-01&end=2025-01-07"
        )
        data = json.loads(response.content)
        assert data["labels"] == []

    @pytest.mark.django_db
    def test_invalid_date_returns_400(self, client):
        response = client.get("/api/metrics/workout-volume?end=bad")
        assert response.status_code == 400

    @pytest.mark.django_db
    def test_post_not_allowed(self, client):
        response = client.post("/api/metrics/workout-volume")
        assert response.status_code == 405


class TestTopRecordTypesAPI:
    @pytest.mark.django_db
    def test_returns_200(self, client, record_data):
        response = client.get("/api/metrics/top-record-types")
        assert response.status_code == 200

    @pytest.mark.django_db
    def test_returns_datasets_per_type(self, client, record_data):
        response = client.get("/api/metrics/top-record-types")
        data = json.loads(response.content)
        assert len(data["datasets"]) == 3

    @pytest.mark.django_db
    def test_types_ordered_by_volume(self, client, record_data):
        response = client.get(
            "/api/metrics/top-record-types?start=2026-03-02&end=2026-03-15"
        )
        data = json.loads(response.content)
        assert data["datasets"][0]["label"] == "HKQuantityTypeIdentifierHeartRate"

    @pytest.mark.django_db
    def test_date_range_filtering(self, client, record_data):
        response = client.get(
            "/api/metrics/top-record-types?start=2026-03-02&end=2026-03-08"
        )
        data = json.loads(response.content)
        assert len(data["labels"]) == 1

    @pytest.mark.django_db
    def test_invalid_date_returns_400(self, client):
        response = client.get("/api/metrics/top-record-types?start=2026-13-01")
        assert response.status_code == 400

    @pytest.mark.django_db
    def test_post_not_allowed(self, client):
        response = client.post("/api/metrics/top-record-types")
        assert response.status_code == 405

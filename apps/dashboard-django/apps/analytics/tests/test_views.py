import pytest
from django.test import Client


@pytest.mark.django_db
def test_index_returns_200():
    client = Client()
    response = client.get("/")
    assert response.status_code == 200


@pytest.mark.django_db
def test_index_contains_chart_root():
    client = Client()
    response = client.get("/")
    assert b'id="chart-root"' in response.content

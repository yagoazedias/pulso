from django.urls import path

from . import views

urlpatterns = [
    path("", views.index, name="index"),
    path("api/metrics/energy-vs-goal", views.energy_vs_goal, name="energy-vs-goal"),
    path("api/metrics/workout-volume", views.workout_volume, name="workout-volume"),
    path("api/metrics/top-record-types", views.top_record_types, name="top-record-types"),
]

import datetime

from django.http import JsonResponse
from django.shortcuts import render
from django.views.decorators.http import require_GET

from apps.analytics.repositories import PostgresMetricsRepository


def index(request):
    return render(request, "analytics/index.html")


def parse_date_params(request):
    """Extract and validate start/end date params from request.

    Returns (start, end) as strings or None.
    Raises ValueError if format is invalid.
    """
    start = request.GET.get("start")
    end = request.GET.get("end")

    for value, name in [(start, "start"), (end, "end")]:
        if value is not None:
            try:
                datetime.date.fromisoformat(value)
            except ValueError:
                raise ValueError(
                    f"Invalid date format for '{name}': '{value}'. Expected YYYY-MM-DD."
                )

    return start, end


@require_GET
def energy_vs_goal(request):
    try:
        start, end = parse_date_params(request)
    except ValueError as e:
        return JsonResponse({"error": str(e)}, status=400)

    repo = PostgresMetricsRepository()
    data = repo.get_energy_vs_goal(start=start, end=end)
    return JsonResponse(data)


@require_GET
def workout_volume(request):
    try:
        start, end = parse_date_params(request)
    except ValueError as e:
        return JsonResponse({"error": str(e)}, status=400)

    repo = PostgresMetricsRepository()
    data = repo.get_workout_volume(start=start, end=end)
    return JsonResponse(data)


@require_GET
def top_record_types(request):
    try:
        start, end = parse_date_params(request)
    except ValueError as e:
        return JsonResponse({"error": str(e)}, status=400)

    repo = PostgresMetricsRepository()
    data = repo.get_top_record_types(start=start, end=end)
    return JsonResponse(data)

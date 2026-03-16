import datetime


class PostgresMetricsRepository:
    def get_energy_vs_goal(self, start=None, end=None):
        from apps.analytics.models import ActivitySummary

        today = datetime.date.today()
        is_default = start is None and end is None

        if start is None:
            start = (today - datetime.timedelta(days=90)).isoformat()
        if end is None:
            end = today.isoformat()

        rows = (
            ActivitySummary.objects.filter(
                date_components__gte=start,
                date_components__lte=end,
            )
            .order_by("date_components")
            .values_list(
                "date_components",
                "active_energy_burned",
                "active_energy_burned_goal",
            )
        )

        labels = []
        energy_data = []
        goal_data = []
        last_date = None

        for date_val, energy, goal in rows:
            labels.append(date_val.isoformat())
            energy_data.append(energy)
            goal_data.append(goal)
            last_date = date_val

        return {
            "labels": labels,
            "datasets": [
                {"label": "Active Energy Burned", "data": energy_data},
                {"label": "Goal", "data": goal_data},
            ],
            "meta": {
                "unit": "kcal",
                "window": "90d" if is_default else "custom",
                "last_updated": last_date.isoformat() if last_date else None,
            },
        }

    def get_workout_volume(self, start=None, end=None):
        from apps.analytics.models import Workout

        today = datetime.date.today()
        is_default = start is None and end is None

        if start is None:
            start = (today - datetime.timedelta(weeks=12)).isoformat()
        if end is None:
            end = today.isoformat()

        workouts = Workout.objects.filter(
            start_date__gte=start,
            start_date__lte=end + "T23:59:59",
        ).order_by("start_date")

        # Group by ISO week (Monday)
        weeks = {}
        for w in workouts:
            # Get the Monday of this workout's week
            dt = w.start_date.date() if hasattr(w.start_date, "date") else w.start_date
            monday = dt - datetime.timedelta(days=dt.weekday())
            key = monday.isoformat()

            if key not in weeks:
                weeks[key] = {"count": 0, "duration": 0.0, "energy": 0.0}

            weeks[key]["count"] += 1
            if w.duration is not None:
                weeks[key]["duration"] += w.duration / 60.0
            if w.total_energy_burned is not None:
                weeks[key]["energy"] += w.total_energy_burned

        sorted_keys = sorted(weeks.keys())
        labels = sorted_keys
        counts = [weeks[k]["count"] for k in sorted_keys]
        durations = [weeks[k]["duration"] for k in sorted_keys]
        energies = [weeks[k]["energy"] for k in sorted_keys]

        last_date = sorted_keys[-1] if sorted_keys else None

        return {
            "labels": labels,
            "datasets": [
                {"label": "Workouts", "data": counts},
                {"label": "Duration (min)", "data": durations},
                {"label": "Energy Burned (kcal)", "data": energies},
            ],
            "meta": {
                "unit": "mixed",
                "window": "12w" if is_default else "custom",
                "last_updated": last_date,
            },
        }

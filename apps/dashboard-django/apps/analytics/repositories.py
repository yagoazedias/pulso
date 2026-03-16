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

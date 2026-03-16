from django.db import models


class ActivitySummary(models.Model):
    date_components = models.DateField()
    active_energy_burned = models.FloatField(null=True)
    active_energy_burned_goal = models.FloatField(null=True)
    active_energy_burned_unit = models.CharField(max_length=50, null=True)

    class Meta:
        managed = False
        db_table = '"public"."activity_summary"'

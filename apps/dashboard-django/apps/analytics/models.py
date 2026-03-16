from django.db import models


class ActivitySummary(models.Model):
    date_components = models.DateField()
    active_energy_burned = models.FloatField(null=True)
    active_energy_burned_goal = models.FloatField(null=True)
    active_energy_burned_unit = models.CharField(max_length=50, null=True)

    class Meta:
        managed = False
        db_table = '"public"."activity_summary"'


class Workout(models.Model):
    activity_type = models.CharField(max_length=255)
    duration = models.FloatField(null=True)
    total_energy_burned = models.FloatField(null=True)
    start_date = models.DateTimeField()
    end_date = models.DateTimeField()

    class Meta:
        managed = False
        db_table = '"public"."workout"'


class Record(models.Model):
    record_type = models.ForeignKey("RecordType", on_delete=models.DO_NOTHING)
    start_date = models.DateTimeField()

    class Meta:
        managed = False
        db_table = '"public"."record"'


class RecordType(models.Model):
    identifier = models.CharField(max_length=255, unique=True)

    class Meta:
        managed = False
        db_table = '"public"."record_type"'

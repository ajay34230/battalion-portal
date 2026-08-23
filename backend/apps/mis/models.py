from django.db import models


class DailySnapshot(models.Model):
    """One row per day, populated by `python manage.py take_daily_snapshot`
    (intended to run on a nightly cron/scheduled task). Trend charts need
    data points over time — there's no way to show 'strength trend over the
    last 90 days' from live tables alone once old leave/personnel records
    change, so this snapshot table is what MIS Dashboard trend endpoints
    actually read from. If the scheduled task hasn't been running, trend
    endpoints will only have data from whenever this was first deployed."""
    date = models.DateField(unique=True)
    total_strength = models.PositiveIntegerField()
    present_strength = models.PositiveIntegerField()
    on_leave = models.PositiveIntegerField()
    in_training = models.PositiveIntegerField()
    vehicles_operational = models.PositiveIntegerField()
    vehicles_total = models.PositiveIntegerField()
    weapons_serviceable = models.PositiveIntegerField()
    weapons_total = models.PositiveIntegerField()
    inventory_items_below_threshold = models.PositiveIntegerField()

    class Meta:
        db_table = "mis_daily_snapshot"
        ordering = ["-date"]

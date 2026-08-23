from django.contrib import admin
from .models import DailySnapshot
@admin.register(DailySnapshot)
class DailySnapshotAdmin(admin.ModelAdmin):
    list_display = ["date", "total_strength", "present_strength", "on_leave", "in_training"]

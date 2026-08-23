from django.contrib import admin
from .models import FiringRecord, PTRecord, BattleTrainingSession, Competition, TrainingAttendance

@admin.register(FiringRecord)
class FiringRecordAdmin(admin.ModelAdmin):
    list_display = ["personnel", "weapon", "date", "score", "qualification"]

@admin.register(PTRecord)
class PTRecordAdmin(admin.ModelAdmin):
    list_display = ["personnel", "date", "run_time_seconds", "pushups", "situps"]

@admin.register(BattleTrainingSession)
class BattleTrainingSessionAdmin(admin.ModelAdmin):
    list_display = ["title", "start_date", "end_date", "location"]

@admin.register(Competition)
class CompetitionAdmin(admin.ModelAdmin):
    list_display = ["name", "date", "level"]

@admin.register(TrainingAttendance)
class TrainingAttendanceAdmin(admin.ModelAdmin):
    list_display = ["personnel", "date", "activity", "status"]

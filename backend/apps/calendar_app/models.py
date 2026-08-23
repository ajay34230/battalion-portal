from django.db import models
from django.conf import settings
from auditlog.registry import auditlog
from apps.companies.models import Company


class CalendarEvent(models.Model):
    class EventType(models.TextChoices):
        TRAINING = "TRAINING", "Training"
        CONFERENCE = "CONFERENCE", "Conference"
        PARADE = "PARADE", "Parade"
        INSPECTION = "INSPECTION", "Inspection"
        LEAVE = "LEAVE", "Leave"
        OTHER = "OTHER", "Other"

    class Scope(models.TextChoices):
        BATTALION = "BATTALION", "Battalion-wide"
        COMPANY = "COMPANY", "Company-specific"

    title = models.CharField(max_length=200)
    event_type = models.CharField(max_length=12, choices=EventType.choices, default=EventType.OTHER)
    scope = models.CharField(max_length=10, choices=Scope.choices, default=Scope.BATTALION)
    company = models.ForeignKey(Company, null=True, blank=True, on_delete=models.CASCADE, related_name="events",
                                 help_text="Required when scope=COMPANY")
    start_datetime = models.DateTimeField()
    end_datetime = models.DateTimeField()
    location = models.CharField(max_length=150, blank=True)
    description = models.TextField(blank=True)
    reminder_minutes_before = models.PositiveIntegerField(
        null=True, blank=True, help_text="Set to enable a reminder this many minutes before start_datetime"
    )
    created_by = models.ForeignKey(settings.AUTH_USER_MODEL, null=True, on_delete=models.SET_NULL, related_name="events_created")
    created_at = models.DateTimeField(auto_now_add=True)

    class Meta:
        db_table = "calendar_event"
        ordering = ["start_datetime"]

    def __str__(self):
        return self.title


auditlog.register(CalendarEvent)

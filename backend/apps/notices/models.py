from django.db import models
from django.conf import settings
from auditlog.registry import auditlog


class Notice(models.Model):
    class Priority(models.TextChoices):
        LOW = "LOW", "Low"
        NORMAL = "NORMAL", "Normal"
        HIGH = "HIGH", "High"
        URGENT = "URGENT", "Urgent"

    class Status(models.TextChoices):
        DRAFT = "DRAFT", "Draft"
        PUBLISHED = "PUBLISHED", "Published"
        ARCHIVED = "ARCHIVED", "Archived"

    title = models.CharField(max_length=200)
    category = models.CharField(max_length=80, blank=True)
    priority = models.CharField(max_length=10, choices=Priority.choices, default=Priority.NORMAL)
    description = models.TextField()
    attachment = models.FileField(upload_to="notice_attachments/", null=True, blank=True)
    status = models.CharField(max_length=10, choices=Status.choices, default=Status.DRAFT)
    published_by = models.ForeignKey(settings.AUTH_USER_MODEL, null=True, on_delete=models.SET_NULL, related_name="notices")
    published_at = models.DateTimeField(null=True, blank=True)
    expiry_date = models.DateField(null=True, blank=True)
    created_at = models.DateTimeField(auto_now_add=True)

    class Meta:
        db_table = "notices_notice"
        ordering = ["-created_at"]

    def __str__(self):
        return self.title


auditlog.register(Notice)

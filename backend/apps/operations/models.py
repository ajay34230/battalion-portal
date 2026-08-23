from django.db import models
from django.conf import settings
from auditlog.registry import auditlog


class OperationalRecord(models.Model):
    """Covers all four spec'd sections (Area Information, Operational Orders,
    Reference Material, Situation Reports) as one model differentiated by
    `section` — they share the same shape (title/body/attachment/access
    control) and splitting into four near-identical models would add
    ceremony without adding capability.

    Access is restricted by role: `restricted_to_roles` works exactly like
    apps.documents.models.Folder.restricted_to_roles — blank means visible to
    every authenticated user, otherwise it's a CSV of apps.accounts.Role
    values. Superusers always see everything."""
    class Section(models.TextChoices):
        AREA_INFO = "AREA_INFO", "Area Information"
        OPERATIONAL_ORDER = "OP_ORDER", "Operational Orders"
        REFERENCE = "REFERENCE", "Reference Material"
        SITREP = "SITREP", "Situation Report"

    section = models.CharField(max_length=15, choices=Section.choices)
    title = models.CharField(max_length=200)
    body = models.TextField(blank=True)
    attachment = models.FileField(upload_to="operations/%Y/%m/", null=True, blank=True)
    restricted_to_roles = models.CharField(max_length=255, blank=True,
                                            help_text="Comma-separated Role values; blank = unrestricted")
    created_by = models.ForeignKey(settings.AUTH_USER_MODEL, null=True, on_delete=models.SET_NULL)
    created_at = models.DateTimeField(auto_now_add=True)
    updated_at = models.DateTimeField(auto_now=True)

    class Meta:
        db_table = "operations_record"
        ordering = ["-created_at"]

    def __str__(self):
        return f"[{self.section}] {self.title}"


auditlog.register(OperationalRecord)

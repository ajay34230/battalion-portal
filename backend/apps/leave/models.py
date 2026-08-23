from django.db import models
from auditlog.registry import auditlog
from apps.personnel.models import Personnel
from apps.accounts.models import User


class LeaveApplication(models.Model):
    class LeaveType(models.TextChoices):
        ANNUAL = "ANNUAL", "Annual Leave"
        CASUAL = "CASUAL", "Casual Leave"
        SICK = "SICK", "Sick Leave"
        EMERGENCY = "EMERGENCY", "Emergency Leave"
        FURLOUGH = "FURLOUGH", "Furlough"

    class Status(models.TextChoices):
        DRAFT = "DRAFT", "Draft"
        PENDING_CLERK = "PENDING_CLERK", "Pending — Company Clerk"
        PENDING_COY_CDR = "PENDING_COY_CDR", "Pending — Company Commander"
        PENDING_ADJT = "PENDING_ADJT", "Pending — Adjutant"
        APPROVED = "APPROVED", "Approved"
        REJECTED = "REJECTED", "Rejected"
        CANCELLED = "CANCELLED", "Cancelled"

    personnel = models.ForeignKey(Personnel, on_delete=models.CASCADE, related_name="leave_applications")
    leave_type = models.CharField(max_length=12, choices=LeaveType.choices)
    start_date = models.DateField()
    end_date = models.DateField()
    reason = models.TextField()
    status = models.CharField(max_length=20, choices=Status.choices, default=Status.DRAFT)

    applied_at = models.DateTimeField(auto_now_add=True)
    updated_at = models.DateTimeField(auto_now=True)

    class Meta:
        db_table = "leave_application"
        ordering = ["-applied_at"]

    def __str__(self):
        return f"{self.personnel.army_number} — {self.leave_type} ({self.status})"

    @property
    def days(self):
        return (self.end_date - self.start_date).days + 1


class LeaveApproval(models.Model):
    """One row per stage of the Soldier -> Clerk -> Coy Cdr -> Adjt workflow.
    Keeps a full, non-editable audit trail of who actioned what and when."""
    class Action(models.TextChoices):
        FORWARDED = "FORWARDED", "Forwarded"
        APPROVED = "APPROVED", "Approved"
        REJECTED = "REJECTED", "Rejected"

    application = models.ForeignKey(LeaveApplication, on_delete=models.CASCADE, related_name="approval_trail")
    stage = models.CharField(max_length=20, choices=LeaveApplication.Status.choices)
    actioned_by = models.ForeignKey(User, on_delete=models.PROTECT)
    action = models.CharField(max_length=12, choices=Action.choices)
    remarks = models.TextField(blank=True)
    actioned_at = models.DateTimeField(auto_now_add=True)

    class Meta:
        db_table = "leave_approval"
        ordering = ["actioned_at"]


auditlog.register(LeaveApplication)
auditlog.register(LeaveApproval)

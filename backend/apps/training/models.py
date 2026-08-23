from django.db import models
from auditlog.registry import auditlog
from apps.personnel.models import Personnel

# Note: formal Courses (nomination, institute, grading) already live on
# apps.personnel.models.Course since they're part of a soldier's permanent
# service record. This app covers the day-to-day training activities that
# are logged repeatedly, not once per course.


class FiringRecord(models.Model):
    class Qualification(models.TextChoices):
        MARKSMAN = "MARKSMAN", "Marksman"
        FIRST_CLASS = "FIRST_CLASS", "First Class"
        SECOND_CLASS = "SECOND_CLASS", "Second Class"
        FAILED = "FAILED", "Failed to Qualify"

    personnel = models.ForeignKey(Personnel, on_delete=models.CASCADE, related_name="firing_records")
    weapon = models.CharField(max_length=80)
    date = models.DateField()
    score = models.PositiveIntegerField()
    max_score = models.PositiveIntegerField(default=100)
    qualification = models.CharField(max_length=15, choices=Qualification.choices)

    class Meta:
        db_table = "training_firing_record"
        ordering = ["-date"]


class PTRecord(models.Model):
    personnel = models.ForeignKey(Personnel, on_delete=models.CASCADE, related_name="pt_records")
    date = models.DateField()
    run_time_seconds = models.PositiveIntegerField(help_text="1.6km/2.4km run time in seconds")
    pushups = models.PositiveIntegerField()
    situps = models.PositiveIntegerField()

    class Meta:
        db_table = "training_pt_record"
        ordering = ["-date"]


class BattleTrainingSession(models.Model):
    title = models.CharField(max_length=150)
    location = models.CharField(max_length=150, blank=True)
    start_date = models.DateField()
    end_date = models.DateField()
    description = models.TextField(blank=True)
    participants = models.ManyToManyField(Personnel, related_name="battle_training_sessions", blank=True)

    class Meta:
        db_table = "training_battle_session"
        ordering = ["-start_date"]

    def __str__(self):
        return self.title


class Competition(models.Model):
    name = models.CharField(max_length=150)
    date = models.DateField()
    level = models.CharField(max_length=80, blank=True, help_text="e.g. Brigade, Division, Army level")
    participants = models.ManyToManyField(Personnel, related_name="competitions", blank=True)
    result = models.CharField(max_length=150, blank=True)

    class Meta:
        db_table = "training_competition"
        ordering = ["-date"]

    def __str__(self):
        return self.name


class TrainingAttendance(models.Model):
    class Status(models.TextChoices):
        PRESENT = "PRESENT", "Present"
        ABSENT = "ABSENT", "Absent"
        EXCUSED = "EXCUSED", "Excused"

    personnel = models.ForeignKey(Personnel, on_delete=models.CASCADE, related_name="training_attendance")
    date = models.DateField()
    activity = models.CharField(max_length=150, help_text="e.g. PT Parade, Weapon Training, Range Practice")
    status = models.CharField(max_length=10, choices=Status.choices, default=Status.PRESENT)

    class Meta:
        db_table = "training_attendance"
        ordering = ["-date"]
        unique_together = ["personnel", "date", "activity"]


for m in (FiringRecord, PTRecord, BattleTrainingSession, Competition, TrainingAttendance):
    auditlog.register(m)

from django.db import models
from auditlog.registry import auditlog
from apps.companies.models import Company, Platoon, Section


class Personnel(models.Model):
    class BloodGroup(models.TextChoices):
        A_POS = "A+", "A+"; A_NEG = "A-", "A-"
        B_POS = "B+", "B+"; B_NEG = "B-", "B-"
        O_POS = "O+", "O+"; O_NEG = "O-", "O-"
        AB_POS = "AB+", "AB+"; AB_NEG = "AB-", "AB-"

    class MedicalCategory(models.TextChoices):
        SHAPE_1 = "SHAPE-1", "SHAPE-1 (Fully Fit)"
        SHAPE_2 = "SHAPE-2", "SHAPE-2"
        SHAPE_3 = "SHAPE-3", "SHAPE-3"
        LOW_MED = "LOW-MED", "Low Medical Category"

    army_number = models.CharField(max_length=20, unique=True, db_index=True)
    rank = models.CharField(max_length=40)
    first_name = models.CharField(max_length=80)
    last_name = models.CharField(max_length=80, blank=True)
    father_name = models.CharField(max_length=120)
    date_of_birth = models.DateField()
    date_of_enrollment = models.DateField()

    company = models.ForeignKey(Company, on_delete=models.PROTECT, related_name="personnel")
    platoon = models.ForeignKey(Platoon, on_delete=models.SET_NULL, null=True, blank=True, related_name="personnel")
    section = models.ForeignKey(Section, on_delete=models.SET_NULL, null=True, blank=True, related_name="personnel")

    appointment = models.CharField(max_length=100, blank=True)
    trade = models.CharField(max_length=100, blank=True)
    blood_group = models.CharField(max_length=4, choices=BloodGroup.choices)
    medical_category = models.CharField(max_length=10, choices=MedicalCategory.choices, default=MedicalCategory.SHAPE_1)

    mobile_number = models.CharField(max_length=15, blank=True)
    emergency_contact_name = models.CharField(max_length=120, blank=True)
    emergency_contact_number = models.CharField(max_length=15, blank=True)
    address = models.TextField(blank=True)
    photograph = models.ImageField(upload_to="personnel_photos/", null=True, blank=True)

    is_active = models.BooleanField(default=True)  # False once struck off / transferred out
    created_at = models.DateTimeField(auto_now_add=True)
    updated_at = models.DateTimeField(auto_now=True)

    class Meta:
        db_table = "personnel_personnel"
        indexes = [models.Index(fields=["company", "is_active"])]

    def __str__(self):
        return f"{self.rank} {self.first_name} {self.last_name} ({self.army_number})"


class ServiceRecordEntry(models.Model):
    personnel = models.ForeignKey(Personnel, on_delete=models.CASCADE, related_name="service_records")
    event_date = models.DateField()
    description = models.TextField()
    order_reference = models.CharField(max_length=100, blank=True)

    class Meta:
        db_table = "personnel_service_record"
        ordering = ["-event_date"]


class Course(models.Model):
    personnel = models.ForeignKey(Personnel, on_delete=models.CASCADE, related_name="courses")
    course_name = models.CharField(max_length=150)
    institute = models.CharField(max_length=150, blank=True)
    start_date = models.DateField()
    end_date = models.DateField()
    grading = models.CharField(max_length=50, blank=True)

    class Meta:
        db_table = "personnel_course"


class Award(models.Model):
    personnel = models.ForeignKey(Personnel, on_delete=models.CASCADE, related_name="awards")
    title = models.CharField(max_length=150)
    awarded_on = models.DateField()
    citation = models.TextField(blank=True)

    class Meta:
        db_table = "personnel_award"


class Punishment(models.Model):
    personnel = models.ForeignKey(Personnel, on_delete=models.CASCADE, related_name="punishments")
    awarded_on = models.DateField()
    details = models.TextField()
    authority = models.CharField(max_length=150, blank=True)

    class Meta:
        db_table = "personnel_punishment"


class Transfer(models.Model):
    personnel = models.ForeignKey(Personnel, on_delete=models.CASCADE, related_name="transfers")
    from_company = models.ForeignKey(Company, on_delete=models.SET_NULL, null=True, related_name="transfers_out")
    to_company = models.ForeignKey(Company, on_delete=models.SET_NULL, null=True, related_name="transfers_in")
    transfer_date = models.DateField()
    order_reference = models.CharField(max_length=100, blank=True)

    class Meta:
        db_table = "personnel_transfer"


for m in (Personnel, ServiceRecordEntry, Course, Award, Punishment, Transfer):
    auditlog.register(m)

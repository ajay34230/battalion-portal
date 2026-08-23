from django.db import models
from auditlog.registry import auditlog


class Company(models.Model):
    class CompanyCode(models.TextChoices):
        HQ = "HQ", "Headquarters Company"
        A = "A", "A Company"
        B = "B", "B Company"
        C = "C", "C Company"
        D = "D", "D Company"

    code = models.CharField(max_length=4, choices=CompanyCode.choices, unique=True)
    name = models.CharField(max_length=100)

    def __str__(self):
        return self.name

    class Meta:
        db_table = "companies_company"
        verbose_name_plural = "companies"


class Platoon(models.Model):
    company = models.ForeignKey(Company, on_delete=models.CASCADE, related_name="platoons")
    name = models.CharField(max_length=50)

    class Meta:
        db_table = "companies_platoon"


class Section(models.Model):
    platoon = models.ForeignKey(Platoon, on_delete=models.CASCADE, related_name="sections")
    name = models.CharField(max_length=50)

    class Meta:
        db_table = "companies_section"


auditlog.register(Company)

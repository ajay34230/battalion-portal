from django.db import models
from auditlog.registry import auditlog
from apps.personnel.models import Personnel


class Vehicle(models.Model):
    vehicle_number = models.CharField(max_length=20, unique=True, db_index=True)
    vehicle_type = models.CharField(max_length=80)
    driver = models.ForeignKey(Personnel, null=True, blank=True, on_delete=models.SET_NULL, related_name="vehicles_driven")
    fuel_status_percent = models.PositiveSmallIntegerField(default=100)
    mileage_km = models.PositiveIntegerField(default=0)
    insurance_expiry = models.DateField(null=True, blank=True)
    last_service_date = models.DateField(null=True, blank=True)
    next_service_due = models.DateField(null=True, blank=True)
    is_operational = models.BooleanField(default=True)

    class Meta:
        db_table = "vehicles_vehicle"

    def __str__(self):
        return f"{self.vehicle_type} — {self.vehicle_number}"


class FuelRecord(models.Model):
    vehicle = models.ForeignKey(Vehicle, on_delete=models.CASCADE, related_name="fuel_records")
    date = models.DateField()
    litres = models.DecimalField(max_digits=6, decimal_places=2)
    odometer_km = models.PositiveIntegerField()

    class Meta:
        db_table = "vehicles_fuel_record"
        ordering = ["-date"]


class ServiceRecord(models.Model):
    vehicle = models.ForeignKey(Vehicle, on_delete=models.CASCADE, related_name="service_records")
    date = models.DateField()
    description = models.TextField()
    cost = models.DecimalField(max_digits=10, decimal_places=2, null=True, blank=True)
    document = models.FileField(upload_to="vehicle_service_docs/", null=True, blank=True)

    class Meta:
        db_table = "vehicles_service_record"
        ordering = ["-date"]


auditlog.register(Vehicle)
auditlog.register(FuelRecord)
auditlog.register(ServiceRecord)

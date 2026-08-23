from django.contrib import admin
from .models import Vehicle, FuelRecord, ServiceRecord
@admin.register(Vehicle)
class VehicleAdmin(admin.ModelAdmin):
    list_display = ["vehicle_number", "vehicle_type", "driver", "is_operational"]
    list_filter = ["vehicle_type", "is_operational"]
@admin.register(FuelRecord)
class FuelRecordAdmin(admin.ModelAdmin):
    list_display = ["vehicle", "date", "litres", "odometer_km"]
@admin.register(ServiceRecord)
class ServiceRecordAdmin(admin.ModelAdmin):
    list_display = ["vehicle", "date", "cost"]

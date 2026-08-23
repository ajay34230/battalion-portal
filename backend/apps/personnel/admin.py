from django.contrib import admin
from .models import Personnel, ServiceRecordEntry, Course, Award, Punishment, Transfer

class ServiceRecordInline(admin.TabularInline):
    model = ServiceRecordEntry
    extra = 0

class CourseInline(admin.TabularInline):
    model = Course
    extra = 0

class AwardInline(admin.TabularInline):
    model = Award
    extra = 0

class PunishmentInline(admin.TabularInline):
    model = Punishment
    extra = 0

@admin.register(Personnel)
class PersonnelAdmin(admin.ModelAdmin):
    list_display = ["army_number", "rank", "first_name", "last_name", "company", "trade", "is_active"]
    list_filter = ["company", "rank", "medical_category", "is_active"]
    search_fields = ["army_number", "first_name", "last_name", "father_name"]
    inlines = [ServiceRecordInline, CourseInline, AwardInline, PunishmentInline]

@admin.register(Transfer)
class TransferAdmin(admin.ModelAdmin):
    list_display = ["personnel", "from_company", "to_company", "transfer_date"]

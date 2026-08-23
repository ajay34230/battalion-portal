from django.contrib import admin
from .models import OperationalRecord
@admin.register(OperationalRecord)
class OperationalRecordAdmin(admin.ModelAdmin):
    list_display = ["section", "title", "restricted_to_roles", "created_by", "created_at"]
    list_filter = ["section"]

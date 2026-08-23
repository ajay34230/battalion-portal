from django.contrib import admin
from .models import LeaveApplication, LeaveApproval

class ApprovalInline(admin.TabularInline):
    model = LeaveApproval
    extra = 0
    readonly_fields = ["stage", "actioned_by", "action", "remarks", "actioned_at"]
    can_delete = False

@admin.register(LeaveApplication)
class LeaveApplicationAdmin(admin.ModelAdmin):
    list_display = ["personnel", "leave_type", "start_date", "end_date", "status"]
    list_filter = ["status", "leave_type"]
    inlines = [ApprovalInline]

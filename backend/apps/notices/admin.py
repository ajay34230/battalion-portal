from django.contrib import admin
from .models import Notice
@admin.register(Notice)
class NoticeAdmin(admin.ModelAdmin):
    list_display = ["title", "priority", "status", "published_by", "expiry_date"]
    list_filter = ["status", "priority"]

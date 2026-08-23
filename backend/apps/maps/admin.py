from django.contrib import admin
from .models import MapFile
@admin.register(MapFile)
class MapFileAdmin(admin.ModelAdmin):
    list_display = ["title", "available_offline", "uploaded_by", "uploaded_at"]

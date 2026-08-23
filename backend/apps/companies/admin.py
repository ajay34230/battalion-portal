from django.contrib import admin
from .models import Company, Platoon, Section

@admin.register(Company)
class CompanyAdmin(admin.ModelAdmin):
    list_display = ["code", "name"]

@admin.register(Platoon)
class PlatoonAdmin(admin.ModelAdmin):
    list_display = ["name", "company"]
    list_filter = ["company"]

@admin.register(Section)
class SectionAdmin(admin.ModelAdmin):
    list_display = ["name", "platoon"]
    list_filter = ["platoon__company"]

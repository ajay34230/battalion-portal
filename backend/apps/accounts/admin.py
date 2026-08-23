from django.contrib import admin
from django.contrib.auth.admin import UserAdmin
from .models import User

@admin.register(User)
class BattalionUserAdmin(UserAdmin):
    list_display = ["username", "rank", "get_full_name", "role", "company", "is_active", "is_active_duty"]
    list_filter = ["role", "company", "is_active", "is_active_duty"]
    search_fields = ["username", "first_name", "last_name", "service_number"]
    fieldsets = UserAdmin.fieldsets + (
        ("Battalion Details", {"fields": ("role", "company", "rank", "service_number",
                                           "mobile_number", "is_active_duty", "must_change_password")}),
    )

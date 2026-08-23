from django.contrib import admin
from .models import Weapon, WeaponTransaction
@admin.register(Weapon)
class WeaponAdmin(admin.ModelAdmin):
    list_display = ["weapon_number", "weapon_type", "condition", "current_holder"]
    list_filter = ["condition", "weapon_type"]
@admin.register(WeaponTransaction)
class WeaponTransactionAdmin(admin.ModelAdmin):
    list_display = ["weapon", "tx_type", "personnel", "tx_date"]

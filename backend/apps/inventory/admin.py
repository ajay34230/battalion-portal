from django.contrib import admin
from .models import Vendor, InventoryItem, StockMovement
@admin.register(Vendor)
class VendorAdmin(admin.ModelAdmin):
    list_display = ["name", "contact_number"]
@admin.register(InventoryItem)
class InventoryItemAdmin(admin.ModelAdmin):
    list_display = ["name", "category", "quantity", "reorder_threshold", "location"]
    list_filter = ["category"]
@admin.register(StockMovement)
class StockMovementAdmin(admin.ModelAdmin):
    list_display = ["item", "move_type", "quantity", "date"]

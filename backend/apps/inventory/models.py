from django.db import models
from auditlog.registry import auditlog


class Vendor(models.Model):
    name = models.CharField(max_length=150)
    contact_number = models.CharField(max_length=15, blank=True)
    address = models.TextField(blank=True)

    class Meta:
        db_table = "inventory_vendor"

    def __str__(self):
        return self.name


class InventoryItem(models.Model):
    name = models.CharField(max_length=150)
    category = models.CharField(max_length=80)
    quantity = models.IntegerField(default=0)
    reorder_threshold = models.IntegerField(default=0, help_text="Alert fires when quantity falls at/below this")
    location = models.CharField(max_length=100, blank=True)
    date_received = models.DateField(null=True, blank=True)
    vendor = models.ForeignKey(Vendor, null=True, blank=True, on_delete=models.SET_NULL, related_name="items")

    class Meta:
        db_table = "inventory_item"

    def __str__(self):
        return f"{self.name} ({self.quantity})"

    @property
    def below_threshold(self):
        return self.quantity <= self.reorder_threshold


class StockMovement(models.Model):
    class MoveType(models.TextChoices):
        ISSUE = "ISSUE", "Issue"
        RECEIPT = "RECEIPT", "Receipt"
        TRANSFER = "TRANSFER", "Transfer"

    item = models.ForeignKey(InventoryItem, on_delete=models.CASCADE, related_name="movements")
    move_type = models.CharField(max_length=10, choices=MoveType.choices)
    quantity = models.IntegerField()
    date = models.DateField()
    reference = models.CharField(max_length=100, blank=True)
    notes = models.TextField(blank=True)

    class Meta:
        db_table = "inventory_stock_movement"
        ordering = ["-date"]


auditlog.register(InventoryItem)
auditlog.register(StockMovement)

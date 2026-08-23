from django.db import models
from auditlog.registry import auditlog
from apps.personnel.models import Personnel


class Weapon(models.Model):
    class Condition(models.TextChoices):
        SERVICEABLE = "SERVICEABLE", "Serviceable"
        UNSERVICEABLE = "UNSERVICEABLE", "Unserviceable"
        UNDER_REPAIR = "UNDER_REPAIR", "Under Repair"
        DUE_INSPECTION = "DUE_INSPECTION", "Due Inspection"

    weapon_number = models.CharField(max_length=30, unique=True, db_index=True)
    weapon_type = models.CharField(max_length=80)  # e.g. INSAS Rifle, Light Machine Gun
    condition = models.CharField(max_length=20, choices=Condition.choices, default=Condition.SERVICEABLE)
    current_holder = models.ForeignKey(
        Personnel, null=True, blank=True, on_delete=models.SET_NULL, related_name="weapons_held"
    )
    last_inspection_date = models.DateField(null=True, blank=True)
    next_inspection_due = models.DateField(null=True, blank=True)
    remarks = models.TextField(blank=True)

    class Meta:
        db_table = "weapons_weapon"

    def __str__(self):
        return f"{self.weapon_type} — {self.weapon_number}"


class WeaponTransaction(models.Model):
    class TxType(models.TextChoices):
        ISSUE = "ISSUE", "Issue"
        RETURN = "RETURN", "Return"
        TRANSFER = "TRANSFER", "Transfer"
        REPAIR_ENTRY = "REPAIR", "Repair Entry"

    weapon = models.ForeignKey(Weapon, on_delete=models.CASCADE, related_name="transactions")
    tx_type = models.CharField(max_length=10, choices=TxType.choices)
    personnel = models.ForeignKey(Personnel, null=True, blank=True, on_delete=models.SET_NULL)
    tx_date = models.DateField()
    notes = models.TextField(blank=True)

    class Meta:
        db_table = "weapons_transaction"
        ordering = ["-tx_date"]


auditlog.register(Weapon)
auditlog.register(WeaponTransaction)

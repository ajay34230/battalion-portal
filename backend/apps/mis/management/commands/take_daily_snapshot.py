"""
Takes today's battalion-wide snapshot for MIS trend reporting.
Run nightly, e.g. via a Windows Task Scheduler job calling into WSL2:
    docker compose exec -T backend python manage.py take_daily_snapshot
"""
from datetime import date
from django.core.management.base import BaseCommand

from apps.personnel.models import Personnel
from apps.leave.models import LeaveApplication
from apps.vehicles.models import Vehicle
from apps.weapons.models import Weapon
from apps.inventory.models import InventoryItem
from apps.mis.models import DailySnapshot


class Command(BaseCommand):
    help = "Populate today's row in mis_daily_snapshot for MIS Dashboard trend charts."

    def handle(self, *args, **options):
        today = date.today()
        personnel = Personnel.objects.filter(is_active=True)
        total = personnel.count()
        on_leave = personnel.filter(
            leave_applications__status=LeaveApplication.Status.APPROVED,
            leave_applications__start_date__lte=today,
            leave_applications__end_date__gte=today,
        ).distinct().count()
        in_training = personnel.filter(
            courses__start_date__lte=today, courses__end_date__gte=today
        ).distinct().count()

        vehicles = Vehicle.objects.all()
        weapons = Weapon.objects.all()
        items = InventoryItem.objects.all()
        below_threshold = sum(1 for i in items if i.below_threshold)

        snapshot, created = DailySnapshot.objects.update_or_create(
            date=today,
            defaults={
                "total_strength": total,
                "present_strength": total - on_leave,
                "on_leave": on_leave,
                "in_training": in_training,
                "vehicles_operational": vehicles.filter(is_operational=True).count(),
                "vehicles_total": vehicles.count(),
                "weapons_serviceable": weapons.filter(condition=Weapon.Condition.SERVICEABLE).count(),
                "weapons_total": weapons.count(),
                "inventory_items_below_threshold": below_threshold,
            },
        )
        verb = "Created" if created else "Updated"
        self.stdout.write(self.style.SUCCESS(f"{verb} snapshot for {today}."))

from datetime import date
from rest_framework.views import APIView
from rest_framework.response import Response
from rest_framework.permissions import IsAuthenticated

from apps.personnel.models import Personnel
from apps.companies.models import Company
from apps.leave.models import LeaveApplication
from apps.weapons.models import Weapon
from apps.vehicles.models import Vehicle
from apps.notices.models import Notice
from apps.accounts.permissions import caps


class DashboardView(APIView):
    """Single call that backs every widget on the main Dashboard page:
    Total/Present Strength, On Leave, In Training, Vehicles Available,
    Weapons Serviceable, Latest Notices, Company-wise Strength.
    (Today's Events / Recent Activities need the Calendar and a generic
    activity-log model respectively — not yet built, see README limitations.)"""
    permission_classes = [IsAuthenticated]

    def get(self, request):
        user = request.user
        personnel_qs = Personnel.objects.filter(is_active=True)
        if not user.is_superuser and not caps(user).get("view_all") and user.company_id:
            personnel_qs = personnel_qs.filter(company_id=user.company_id)

        total_strength = personnel_qs.count()
        on_leave_ids = LeaveApplication.objects.filter(
            personnel__in=personnel_qs, status=LeaveApplication.Status.APPROVED,
            start_date__lte=date.today(), end_date__gte=date.today(),
        ).values_list("personnel_id", flat=True)
        on_leave = personnel_qs.filter(id__in=on_leave_ids).count()

        weapons_qs = Weapon.objects.all()
        vehicles_qs = Vehicle.objects.all()

        company_breakdown = []
        for company in Company.objects.all():
            coy_personnel = personnel_qs.filter(company=company)
            company_breakdown.append({
                "code": company.code,
                "name": company.name,
                "total": coy_personnel.count(),
            })

        return Response({
            "total_strength": total_strength,
            "present_strength": total_strength - on_leave,
            "on_leave": on_leave,
            "in_training": personnel_qs.filter(
                courses__start_date__lte=date.today(), courses__end_date__gte=date.today()
            ).distinct().count(),
            "vehicles": {
                "total": vehicles_qs.count(),
                "operational": vehicles_qs.filter(is_operational=True).count(),
            },
            "weapons": {
                "total": weapons_qs.count(),
                "serviceable": weapons_qs.filter(condition=Weapon.Condition.SERVICEABLE).count(),
            },
            "latest_notices": list(
                Notice.objects.filter(status=Notice.Status.PUBLISHED)
                .order_by("-published_at")[:5]
                .values("id", "title", "priority", "published_at")
            ),
            "company_breakdown": company_breakdown,
        })

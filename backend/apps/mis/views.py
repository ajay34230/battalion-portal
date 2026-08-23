from rest_framework import serializers, viewsets
from rest_framework.decorators import action
from rest_framework.response import Response
from rest_framework.permissions import IsAuthenticated

from .models import DailySnapshot
from apps.accounts.permissions import caps


class DailySnapshotSerializer(serializers.ModelSerializer):
    class Meta:
        model = DailySnapshot
        fields = ["date", "total_strength", "present_strength", "on_leave", "in_training",
                  "vehicles_operational", "vehicles_total", "weapons_serviceable",
                  "weapons_total", "inventory_items_below_threshold"]


class MISDashboardView(viewsets.ViewSet):
    """GET /api/mis/trends/?days=90 — Strength/Leave/Training/Vehicle-Readiness/
    Inventory trend series for charting, as spec'd. Restricted to view_all
    roles since it's battalion-wide command-level analytics, not a per-
    company view."""
    permission_classes = [IsAuthenticated]

    @action(detail=False, methods=["get"])
    def trends(self, request):
        from rest_framework import status
        if not request.user.is_superuser and not caps(request.user).get("view_all"):
            return Response({"detail": "MIS Dashboard is restricted to command-level roles."},
                             status=status.HTTP_403_FORBIDDEN)

        try:
            days = int(request.query_params.get("days", 90))
        except ValueError:
            return Response({"detail": "days must be an integer."}, status=status.HTTP_400_BAD_REQUEST)

        snapshots = DailySnapshot.objects.all()[:days]
        data = DailySnapshotSerializer(snapshots, many=True).data
        return Response({
            "snapshot_count": len(data),
            "note": None if data else (
                "No snapshots yet — run `python manage.py take_daily_snapshot` "
                "(ideally on a nightly schedule) to start building trend history."
            ),
            "series": list(reversed(data)),  # oldest first, for charting left-to-right
        })

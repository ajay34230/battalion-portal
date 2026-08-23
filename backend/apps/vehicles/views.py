from rest_framework import serializers, viewsets
from rest_framework.decorators import action
from rest_framework.response import Response
from rest_framework.permissions import IsAuthenticated
from django.db.models import Sum
from django.utils import timezone

from .models import Vehicle, FuelRecord, ServiceRecord
from apps.accounts.permissions import ModuleScopedPermission

VehicleModulePermission = ModuleScopedPermission.for_module("vehicles")


class FuelRecordSerializer(serializers.ModelSerializer):
    class Meta:
        model = FuelRecord
        fields = ["id", "vehicle", "date", "litres", "odometer_km"]


class ServiceRecordSerializer(serializers.ModelSerializer):
    class Meta:
        model = ServiceRecord
        fields = ["id", "vehicle", "date", "description", "cost", "document"]


class VehicleSerializer(serializers.ModelSerializer):
    class Meta:
        model = Vehicle
        fields = ["id", "vehicle_number", "vehicle_type", "driver", "fuel_status_percent",
                  "mileage_km", "insurance_expiry", "last_service_date", "next_service_due", "is_operational"]


class VehicleViewSet(viewsets.ModelViewSet):
    queryset = Vehicle.objects.select_related("driver")
    serializer_class = VehicleSerializer
    permission_classes = [IsAuthenticated, VehicleModulePermission]
    filterset_fields = ["vehicle_type", "is_operational"]

    @action(detail=False, methods=["get"])
    def dashboard(self, request):
        qs = self.get_queryset()
        today = timezone.now().date()
        return Response({
            "total": qs.count(),
            "operational": qs.filter(is_operational=True).count(),
            "service_due": VehicleSerializer(qs.filter(next_service_due__lte=today), many=True).data,
        })

    @action(detail=False, methods=["get"])
    def fuel_consumption_report(self, request):
        totals = (FuelRecord.objects.values("vehicle__vehicle_number")
                  .annotate(total_litres=Sum("litres")).order_by("-total_litres"))
        return Response(list(totals))


class FuelRecordViewSet(viewsets.ModelViewSet):
    queryset = FuelRecord.objects.select_related("vehicle")
    serializer_class = FuelRecordSerializer
    permission_classes = [IsAuthenticated, VehicleModulePermission]
    filterset_fields = ["vehicle"]


class ServiceRecordViewSet(viewsets.ModelViewSet):
    queryset = ServiceRecord.objects.select_related("vehicle")
    serializer_class = ServiceRecordSerializer
    permission_classes = [IsAuthenticated, VehicleModulePermission]
    filterset_fields = ["vehicle"]

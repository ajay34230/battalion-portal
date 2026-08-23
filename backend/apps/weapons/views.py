from rest_framework import serializers, viewsets, status
from rest_framework.decorators import action
from rest_framework.response import Response
from rest_framework.permissions import IsAuthenticated
from django.utils import timezone

from .models import Weapon, WeaponTransaction


class WeaponTransactionSerializer(serializers.ModelSerializer):
    class Meta:
        model = WeaponTransaction
        fields = ["id", "weapon", "tx_type", "personnel", "tx_date", "notes"]


class WeaponSerializer(serializers.ModelSerializer):
    class Meta:
        model = Weapon
        fields = ["id", "weapon_number", "weapon_type", "condition", "current_holder",
                  "last_inspection_date", "next_inspection_due", "remarks"]


class WeaponViewSet(viewsets.ModelViewSet):
    queryset = Weapon.objects.select_related("current_holder")
    serializer_class = WeaponSerializer
    permission_classes = [IsAuthenticated]
    filterset_fields = ["condition", "weapon_type"]

    @action(detail=False, methods=["get"])
    def dashboard(self, request):
        """Weapon status counts + pending-maintenance list, for the module's own dashboard widget."""
        qs = self.get_queryset()
        today = timezone.now().date()
        return Response({
            "total": qs.count(),
            "by_condition": {c: qs.filter(condition=c).count() for c, _ in Weapon.Condition.choices},
            "due_inspection": WeaponSerializer(
                qs.filter(next_inspection_due__lte=today).exclude(condition=Weapon.Condition.UNSERVICEABLE),
                many=True
            ).data,
        })

    @action(detail=True, methods=["post"])
    def issue(self, request, pk=None):
        weapon = self.get_object()
        personnel_id = request.data.get("personnel")
        if not personnel_id:
            return Response({"detail": "personnel is required to issue a weapon."}, status=status.HTTP_400_BAD_REQUEST)
        weapon.current_holder_id = personnel_id
        weapon.save(update_fields=["current_holder"])
        WeaponTransaction.objects.create(
            weapon=weapon, tx_type=WeaponTransaction.TxType.ISSUE,
            personnel_id=personnel_id, tx_date=timezone.now().date(),
            notes=request.data.get("notes", ""),
        )
        return Response(WeaponSerializer(weapon).data)

    @action(detail=True, methods=["post"])
    def returned(self, request, pk=None):
        weapon = self.get_object()
        prev_holder = weapon.current_holder_id
        weapon.current_holder = None
        weapon.save(update_fields=["current_holder"])
        WeaponTransaction.objects.create(
            weapon=weapon, tx_type=WeaponTransaction.TxType.RETURN,
            personnel_id=prev_holder, tx_date=timezone.now().date(),
            notes=request.data.get("notes", ""),
        )
        return Response(WeaponSerializer(weapon).data)

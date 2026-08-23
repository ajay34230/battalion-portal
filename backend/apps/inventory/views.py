from django.db import transaction
from rest_framework import serializers, viewsets, status
from rest_framework.decorators import action
from rest_framework.response import Response
from rest_framework.permissions import IsAuthenticated

from .models import InventoryItem, StockMovement, Vendor
from apps.accounts.permissions import ModuleScopedPermission

InventoryModulePermission = ModuleScopedPermission.for_module("inventory")


class VendorSerializer(serializers.ModelSerializer):
    class Meta:
        model = Vendor
        fields = ["id", "name", "contact_number", "address"]


class InventoryItemSerializer(serializers.ModelSerializer):
    below_threshold = serializers.ReadOnlyField()

    class Meta:
        model = InventoryItem
        fields = ["id", "name", "category", "quantity", "reorder_threshold",
                  "location", "date_received", "vendor", "below_threshold"]


class StockMovementSerializer(serializers.ModelSerializer):
    class Meta:
        model = StockMovement
        fields = ["id", "item", "move_type", "quantity", "date", "reference", "notes"]


class InventoryItemViewSet(viewsets.ModelViewSet):
    queryset = InventoryItem.objects.select_related("vendor")
    serializer_class = InventoryItemSerializer
    permission_classes = [IsAuthenticated, InventoryModulePermission]
    filterset_fields = ["category", "vendor"]

    @action(detail=False, methods=["get"])
    def shortage_report(self, request):
        items = [i for i in self.get_queryset() if i.below_threshold]
        return Response(InventoryItemSerializer(items, many=True).data)

    @action(detail=True, methods=["get"])
    def ledger(self, request, pk=None):
        item = self.get_object()
        return Response(StockMovementSerializer(item.movements.all(), many=True).data)


class StockMovementViewSet(viewsets.ModelViewSet):
    """Creating a movement atomically adjusts InventoryItem.quantity — issue
    decrements, receipt increments, transfer is logged but quantity-neutral
    at the battalion level (location field records where it went)."""
    queryset = StockMovement.objects.select_related("item")
    serializer_class = StockMovementSerializer
    permission_classes = [IsAuthenticated, InventoryModulePermission]
    filterset_fields = ["item", "move_type"]

    def create(self, request, *args, **kwargs):
        serializer = self.get_serializer(data=request.data)
        serializer.is_valid(raise_exception=True)
        item_id = serializer.validated_data["item"].id
        move_type = serializer.validated_data["move_type"]
        qty = serializer.validated_data["quantity"]

        with transaction.atomic():
            # Lock the row for the duration of this transaction so two
            # concurrent issues against the same item can't both pass the
            # stock check and drive quantity negative.
            item = InventoryItem.objects.select_for_update().get(id=item_id)

            if move_type == StockMovement.MoveType.ISSUE and qty > item.quantity:
                return Response({"detail": f"Cannot issue {qty}; only {item.quantity} in stock."},
                                 status=status.HTTP_400_BAD_REQUEST)

            movement = serializer.save()
            if move_type == StockMovement.MoveType.ISSUE:
                item.quantity -= qty
            elif move_type == StockMovement.MoveType.RECEIPT:
                item.quantity += qty
            item.save(update_fields=["quantity"])

        return Response(self.get_serializer(movement).data, status=status.HTTP_201_CREATED)


class VendorViewSet(viewsets.ModelViewSet):
    queryset = Vendor.objects.all()
    serializer_class = VendorSerializer
    permission_classes = [IsAuthenticated, InventoryModulePermission]

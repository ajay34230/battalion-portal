from rest_framework import serializers, viewsets, permissions as drf_permissions
from rest_framework.permissions import IsAuthenticated

from .models import OperationalRecord
from apps.accounts.permissions import caps


def user_can_access(user, record):
    if user.is_superuser or not record.restricted_to_roles:
        return True
    allowed = [r.strip() for r in record.restricted_to_roles.split(",")]
    return user.role in allowed


class _CanManageOperations(drf_permissions.BasePermission):
    def has_permission(self, request, view):
        user = request.user
        return user.is_authenticated and (user.is_superuser or caps(user).get("view_all") or caps(user).get("edit_all"))


class OperationalRecordSerializer(serializers.ModelSerializer):
    class Meta:
        model = OperationalRecord
        fields = ["id", "section", "title", "body", "attachment", "restricted_to_roles",
                  "created_by", "created_at", "updated_at"]
        read_only_fields = ["created_by", "created_at", "updated_at"]


class OperationalRecordViewSet(viewsets.ModelViewSet):
    """Same restricted_to_roles pattern as apps.documents.Folder — see that
    module's rationale. Reading is filtered per-record by role; writing is
    further restricted to command-level (view_all) or edit_all roles — an
    ordinary Clerk or Read Only user shouldn't be able to author operational
    orders even for an unrestricted section."""
    serializer_class = OperationalRecordSerializer
    permission_classes = [IsAuthenticated]
    filterset_fields = ["section"]

    def get_permissions(self):
        if self.action in ("create", "update", "partial_update", "destroy"):
            return [IsAuthenticated(), _CanManageOperations()]
        return [IsAuthenticated()]

    def get_queryset(self):
        user = self.request.user
        if user.is_superuser:
            return OperationalRecord.objects.all()
        accessible_ids = [
            r.id for r in OperationalRecord.objects.only("id", "restricted_to_roles")
            if user_can_access(user, r)
        ]
        return OperationalRecord.objects.filter(id__in=accessible_ids)

    def perform_create(self, serializer):
        serializer.save(created_by=self.request.user)

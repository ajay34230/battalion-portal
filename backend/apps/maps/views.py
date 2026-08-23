from rest_framework import serializers, viewsets, permissions as drf_permissions
from rest_framework.permissions import IsAuthenticated

from .models import MapFile
from apps.accounts.permissions import caps


class _CanManageMaps(drf_permissions.BasePermission):
    def has_permission(self, request, view):
        user = request.user
        return user.is_authenticated and (user.is_superuser or caps(user).get("view_all") or caps(user).get("edit_all"))


class MapFileSerializer(serializers.ModelSerializer):
    class Meta:
        model = MapFile
        fields = ["id", "title", "file", "description", "north_lat", "south_lat",
                  "east_lng", "west_lng", "available_offline", "uploaded_by", "uploaded_at"]
        read_only_fields = ["uploaded_by", "uploaded_at"]


class MapFileViewSet(viewsets.ModelViewSet):
    """Maps sit alongside Operations content in the spec (both under the
    restricted 'Operations' sidebar section) — uploading/editing is
    restricted to command-level (view_all) roles or edit_all, not every
    authenticated user, even though reading stays open to anyone logged in."""
    queryset = MapFile.objects.all()
    serializer_class = MapFileSerializer
    permission_classes = [IsAuthenticated]
    filterset_fields = ["available_offline"]

    def get_permissions(self):
        if self.action in ("create", "update", "partial_update", "destroy"):
            return [IsAuthenticated(), _CanManageMaps()]
        return [IsAuthenticated()]

    def perform_create(self, serializer):
        serializer.save(uploaded_by=self.request.user)

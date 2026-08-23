from django.db.models import Q
from rest_framework import serializers, viewsets, status, permissions as drf_permissions
from rest_framework.decorators import action
from rest_framework.response import Response
from rest_framework.permissions import IsAuthenticated
from rest_framework.exceptions import PermissionDenied

from .models import Folder, Document, DocumentVersion
from apps.accounts.permissions import caps


def accessible_folder_ids(user):
    """Returns a queryset of Folder ids the user's role is allowed to see.
    Superusers and unrestricted folders (blank restricted_to_roles) are always
    included; role-restricted folders are matched by a CSV `contains` check."""
    if user.is_superuser:
        return Folder.objects.values_list("id", flat=True)
    return Folder.objects.filter(
        Q(restricted_to_roles="") | Q(restricted_to_roles__icontains=user.role)
    ).values_list("id", flat=True)


class _CanManageFolders(drf_permissions.BasePermission):
    """Folder structure (including setting restricted_to_roles, which controls
    who can see everything inside) is command-level, not something every
    authenticated user should touch — same reasoning as Maps/Operations."""
    def has_permission(self, request, view):
        user = request.user
        return user.is_authenticated and (user.is_superuser or caps(user).get("view_all") or caps(user).get("edit_all"))


class _NotReadOnly(drf_permissions.BasePermission):
    def has_permission(self, request, view):
        user = request.user
        return user.is_authenticated and (user.is_superuser or not caps(user).get("read_only"))


class DocumentVersionSerializer(serializers.ModelSerializer):
    class Meta:
        model = DocumentVersion
        fields = ["id", "document", "file", "uploaded_by", "uploaded_at", "note"]
        read_only_fields = ["uploaded_by", "uploaded_at"]


class DocumentSerializer(serializers.ModelSerializer):
    class Meta:
        model = Document
        fields = ["id", "folder", "name", "file", "uploaded_by", "uploaded_at", "is_archived"]
        read_only_fields = ["uploaded_by", "uploaded_at"]


class FolderSerializer(serializers.ModelSerializer):
    path = serializers.SerializerMethodField()

    class Meta:
        model = Folder
        fields = ["id", "name", "parent", "restricted_to_roles", "path"]

    def get_path(self, obj):
        return obj.path()


class FolderViewSet(viewsets.ModelViewSet):
    serializer_class = FolderSerializer
    permission_classes = [IsAuthenticated]

    def get_permissions(self):
        if self.action in ("create", "update", "partial_update", "destroy"):
            return [IsAuthenticated(), _CanManageFolders()]
        return [IsAuthenticated()]

    def get_queryset(self):
        return Folder.objects.filter(id__in=accessible_folder_ids(self.request.user))


class DocumentViewSet(viewsets.ModelViewSet):
    serializer_class = DocumentSerializer
    permission_classes = [IsAuthenticated]
    filterset_fields = ["folder", "is_archived"]

    def get_permissions(self):
        if self.action in ("create", "update", "partial_update", "destroy", "new_version"):
            return [IsAuthenticated(), _NotReadOnly()]
        return [IsAuthenticated()]

    def get_queryset(self):
        return Document.objects.select_related("folder").filter(
            folder_id__in=accessible_folder_ids(self.request.user)
        )

    def perform_create(self, serializer):
        # get_queryset() gates reads by folder visibility, but that alone
        # doesn't stop someone from uploading straight into a folder id they
        # can't even see — check the target folder explicitly.
        folder = serializer.validated_data["folder"]
        if folder.id not in accessible_folder_ids(self.request.user):
            raise PermissionDenied("You don't have access to this folder.")
        serializer.save(uploaded_by=self.request.user)

    @action(detail=True, methods=["post"])
    def new_version(self, request, pk=None):
        document = self.get_object()
        version = DocumentVersion.objects.create(
            document=document, file=request.data["file"],
            uploaded_by=request.user, note=request.data.get("note", ""),
        )
        return Response(DocumentVersionSerializer(version).data, status=status.HTTP_201_CREATED)

    @action(detail=True, methods=["get"])
    def versions(self, request, pk=None):
        document = self.get_object()
        return Response(DocumentVersionSerializer(document.versions.all(), many=True).data)

    @action(detail=False, methods=["get"])
    def search(self, request):
        query = request.query_params.get("q", "")
        results = self.get_queryset().filter(name__icontains=query)
        return Response(DocumentSerializer(results, many=True).data)

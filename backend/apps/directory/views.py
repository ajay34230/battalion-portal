from rest_framework import serializers, viewsets
from rest_framework.permissions import IsAuthenticated

from apps.personnel.models import Personnel


class DirectoryEntrySerializer(serializers.ModelSerializer):
    full_name = serializers.SerializerMethodField()
    company_name = serializers.CharField(source="company.name", read_only=True)

    class Meta:
        model = Personnel
        fields = ["id", "army_number", "rank", "full_name", "appointment",
                  "mobile_number", "company_name"]

    def get_full_name(self, obj):
        return f"{obj.first_name} {obj.last_name}".strip()


class DirectoryViewSet(viewsets.ReadOnlyModelViewSet):
    """Contact directory — read-only, searchable by rank and company, as spec'd.
    Deliberately exposes only name/rank/appointment/mobile/company, not the
    full Personnel record (DOB, address, medical category, etc. stay out of
    a directory anyone in the battalion can browse)."""
    serializer_class = DirectoryEntrySerializer
    permission_classes = [IsAuthenticated]
    filterset_fields = ["rank", "company"]

    def get_queryset(self):
        qs = Personnel.objects.filter(is_active=True).select_related("company")
        query = self.request.query_params.get("q")
        if query:
            qs = qs.filter(first_name__icontains=query) | qs.filter(last_name__icontains=query) | qs.filter(rank__icontains=query)
        return qs

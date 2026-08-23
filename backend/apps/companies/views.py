from datetime import date
from rest_framework import serializers, viewsets
from rest_framework.decorators import action
from rest_framework.response import Response
from rest_framework.permissions import IsAuthenticated

from .models import Company, Platoon, Section
from apps.personnel.models import Personnel
from apps.leave.models import LeaveApplication
from apps.accounts.permissions import IsAdjutantOrSuperuser


class SectionSerializer(serializers.ModelSerializer):
    class Meta:
        model = Section
        fields = ["id", "platoon", "name"]


class PlatoonSerializer(serializers.ModelSerializer):
    sections = SectionSerializer(many=True, read_only=True)

    class Meta:
        model = Platoon
        fields = ["id", "company", "name", "sections"]


class CompanySerializer(serializers.ModelSerializer):
    class Meta:
        model = Company
        fields = ["id", "code", "name"]


class CompanyViewSet(viewsets.ModelViewSet):
    queryset = Company.objects.all()
    serializer_class = CompanySerializer
    permission_classes = [IsAuthenticated]

    def get_permissions(self):
        # Companies (HQ/A/B/C/D) are structural — creating/editing/deleting
        # them is an Adjutant/superuser action, not something every
        # authenticated user should be able to do. Reads stay open.
        if self.action in ("create", "update", "partial_update", "destroy"):
            return [IsAuthenticated(), IsAdjutantOrSuperuser()]
        return [IsAuthenticated()]

    @action(detail=True, methods=["get"])
    def state(self, request, pk=None):
        """Total/present strength, leave state, course state, personnel list —
        matches the 'Each company page must display' spec."""
        company = self.get_object()
        personnel = Personnel.objects.filter(company=company, is_active=True)
        today = date.today()
        on_leave_ids = LeaveApplication.objects.filter(
            personnel__company=company, status=LeaveApplication.Status.APPROVED,
            start_date__lte=today, end_date__gte=today,
        ).values_list("personnel_id", flat=True)

        total = personnel.count()
        on_leave = personnel.filter(id__in=on_leave_ids).count()

        from apps.personnel.serializers import PersonnelListSerializer
        return Response({
            "company": CompanySerializer(company).data,
            "total_strength": total,
            "present_strength": total - on_leave,
            "on_leave": on_leave,
            "in_courses": personnel.filter(courses__start_date__lte=today, courses__end_date__gte=today).distinct().count(),
            "personnel": PersonnelListSerializer(personnel, many=True).data,
        })


class PlatoonViewSet(viewsets.ModelViewSet):
    queryset = Platoon.objects.select_related("company")
    serializer_class = PlatoonSerializer
    permission_classes = [IsAuthenticated]
    filterset_fields = ["company"]


class SectionViewSet(viewsets.ModelViewSet):
    queryset = Section.objects.select_related("platoon")
    serializer_class = SectionSerializer
    permission_classes = [IsAuthenticated]
    filterset_fields = ["platoon"]

import io
from django.http import HttpResponse
from rest_framework import viewsets
from rest_framework.decorators import action
from rest_framework.response import Response
from rest_framework.permissions import IsAuthenticated

from .models import Personnel, Course
from .serializers import PersonnelListSerializer, PersonnelDetailSerializer, CourseSerializer
from apps.accounts.permissions import ReadOnlyOrHasEditRights
from apps.accounts.models import Role


class PersonnelViewSet(viewsets.ModelViewSet):
    permission_classes = [IsAuthenticated, ReadOnlyOrHasEditRights]
    filterset_fields = ["company", "platoon", "rank", "trade", "is_active", "blood_group"]

    def get_queryset(self):
        qs = Personnel.objects.select_related("company", "platoon", "section")
        user = self.request.user
        # Company-scoped roles (JCO, Clerk, Company Commander) only see their own company
        if not user.is_superuser and user.role in (Role.JCO, Role.CLERK, Role.COMPANY_COMMANDER) and user.company_id:
            qs = qs.filter(company_id=user.company_id)
        query = self.request.query_params.get("q")
        if query:
            qs = qs.filter(army_number__icontains=query) | qs.filter(first_name__icontains=query) | qs.filter(last_name__icontains=query)
        return qs

    def get_serializer_class(self):
        # Full detail serializer for anything that reads or writes a single
        # record — list uses the lightweight one for smaller payloads.
        # (Previously 'create'/'update' fell through to the list serializer,
        # which is missing required fields like father_name/date_of_birth —
        # every create/update would have failed.)
        if self.action == "list":
            return PersonnelListSerializer
        return PersonnelDetailSerializer

    @action(detail=True, methods=["get"])
    def profile(self, request, pk=None):
        """Complete biodata + courses + leave history + awards + punishments +
        medical category + service record, in one payload — matches the
        'Personnel Profile' page spec."""
        instance = self.get_object()
        return Response(PersonnelDetailSerializer(instance).data)

    @action(detail=False, methods=["get"])
    def export_excel(self, request):
        from openpyxl import Workbook
        wb = Workbook()
        ws = wb.active
        ws.title = "Personnel"
        headers = ["Army No", "Rank", "Name", "Company", "Trade", "Blood Group", "Mobile", "Active"]
        ws.append(headers)
        for p in self.get_queryset():
            ws.append([p.army_number, p.rank, f"{p.first_name} {p.last_name}".strip(),
                       p.company.name if p.company_id else "", p.trade, p.blood_group,
                       p.mobile_number, "Yes" if p.is_active else "No"])
        for col_cells in ws.columns:
            width = max(len(str(c.value)) for c in col_cells if c.value) + 2
            ws.column_dimensions[col_cells[0].column_letter].width = width

        buffer = io.BytesIO()
        wb.save(buffer)
        buffer.seek(0)
        response = HttpResponse(
            buffer.read(),
            content_type="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
        )
        response["Content-Disposition"] = 'attachment; filename="personnel_export.xlsx"'
        return response

    @action(detail=False, methods=["get"])
    def export_pdf(self, request):
        from reportlab.lib import colors
        from reportlab.lib.pagesizes import landscape, A4
        from reportlab.platypus import SimpleDocTemplate, Table, TableStyle, Paragraph
        from reportlab.lib.styles import getSampleStyleSheet

        buffer = io.BytesIO()
        doc = SimpleDocTemplate(buffer, pagesize=landscape(A4))
        styles = getSampleStyleSheet()
        elements = [Paragraph("15 Rajputana Rifles — Personnel Nominal Roll", styles["Title"])]

        data = [["Army No", "Rank", "Name", "Company", "Trade", "Blood Gp", "Mobile", "Status"]]
        for p in self.get_queryset():
            data.append([p.army_number, p.rank, f"{p.first_name} {p.last_name}".strip(),
                         p.company.name if p.company_id else "-", p.trade, p.blood_group,
                         p.mobile_number, "Active" if p.is_active else "Inactive"])

        table = Table(data, repeatRows=1)
        table.setStyle(TableStyle([
            ("BACKGROUND", (0, 0), (-1, 0), colors.HexColor("#14301e")),
            ("TEXTCOLOR", (0, 0), (-1, 0), colors.white),
            ("FONTSIZE", (0, 0), (-1, -1), 8),
            ("GRID", (0, 0), (-1, -1), 0.5, colors.grey),
            ("ROWBACKGROUNDS", (0, 1), (-1, -1), [colors.white, colors.HexColor("#f3f4f2")]),
        ]))
        elements.append(table)
        doc.build(elements)
        buffer.seek(0)

        response = HttpResponse(buffer.read(), content_type="application/pdf")
        response["Content-Disposition"] = 'attachment; filename="personnel_nominal_roll.pdf"'
        return response


class CourseViewSet(viewsets.ModelViewSet):
    """Standalone Course management — same depth as Leave: full CRUD,
    filterable, not just a read-only nested list inside a personnel profile."""
    queryset = Course.objects.select_related("personnel", "personnel__company")
    serializer_class = CourseSerializer
    permission_classes = [IsAuthenticated, ReadOnlyOrHasEditRights]
    filterset_fields = ["personnel", "personnel__company", "course_name"]

    def get_queryset(self):
        qs = super().get_queryset()
        user = self.request.user
        if not user.is_superuser and user.role in (Role.JCO, Role.CLERK, Role.COMPANY_COMMANDER) and user.company_id:
            qs = qs.filter(personnel__company_id=user.company_id)
        return qs

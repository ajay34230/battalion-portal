from django.db.models import Avg, Count
from rest_framework import serializers, viewsets, status
from rest_framework.decorators import action
from rest_framework.response import Response
from rest_framework.permissions import IsAuthenticated

from .models import FiringRecord, PTRecord, BattleTrainingSession, Competition, TrainingAttendance
from apps.accounts.permissions import ModuleScopedPermission

TrainingModulePermission = ModuleScopedPermission.for_module("training")


class FiringRecordSerializer(serializers.ModelSerializer):
    class Meta:
        model = FiringRecord
        fields = ["id", "personnel", "weapon", "date", "score", "max_score", "qualification"]


class PTRecordSerializer(serializers.ModelSerializer):
    class Meta:
        model = PTRecord
        fields = ["id", "personnel", "date", "run_time_seconds", "pushups", "situps"]


class BattleTrainingSessionSerializer(serializers.ModelSerializer):
    class Meta:
        model = BattleTrainingSession
        fields = ["id", "title", "location", "start_date", "end_date", "description", "participants"]


class CompetitionSerializer(serializers.ModelSerializer):
    class Meta:
        model = Competition
        fields = ["id", "name", "date", "level", "participants", "result"]


class TrainingAttendanceSerializer(serializers.ModelSerializer):
    class Meta:
        model = TrainingAttendance
        fields = ["id", "personnel", "date", "activity", "status"]


class FiringRecordViewSet(viewsets.ModelViewSet):
    queryset = FiringRecord.objects.select_related("personnel")
    serializer_class = FiringRecordSerializer
    permission_classes = [IsAuthenticated, TrainingModulePermission]
    filterset_fields = ["personnel", "qualification", "weapon"]


class PTRecordViewSet(viewsets.ModelViewSet):
    queryset = PTRecord.objects.select_related("personnel")
    serializer_class = PTRecordSerializer
    permission_classes = [IsAuthenticated, TrainingModulePermission]
    filterset_fields = ["personnel"]


class BattleTrainingSessionViewSet(viewsets.ModelViewSet):
    queryset = BattleTrainingSession.objects.all()
    serializer_class = BattleTrainingSessionSerializer
    permission_classes = [IsAuthenticated, TrainingModulePermission]


class CompetitionViewSet(viewsets.ModelViewSet):
    queryset = Competition.objects.all()
    serializer_class = CompetitionSerializer
    permission_classes = [IsAuthenticated, TrainingModulePermission]


class TrainingAttendanceViewSet(viewsets.ModelViewSet):
    queryset = TrainingAttendance.objects.select_related("personnel")
    serializer_class = TrainingAttendanceSerializer
    permission_classes = [IsAuthenticated, TrainingModulePermission]
    filterset_fields = ["personnel", "activity", "status", "date"]


class TrainingReportView(viewsets.ViewSet):
    """Individual / Company / Battalion level training reports, as spec'd.
    GET /api/training/reports/individual/?personnel=<id>
    GET /api/training/reports/company/?company=<id>
    GET /api/training/reports/battalion/
    """
    permission_classes = [IsAuthenticated]

    @action(detail=False, methods=["get"])
    def individual(self, request):
        personnel_id = request.query_params.get("personnel")
        if not personnel_id:
            return Response({"detail": "personnel query param is required."}, status=status.HTTP_400_BAD_REQUEST)
        firing = FiringRecord.objects.filter(personnel_id=personnel_id)
        pt = PTRecord.objects.filter(personnel_id=personnel_id)
        attendance = TrainingAttendance.objects.filter(personnel_id=personnel_id)
        total_attendance = attendance.count()
        present = attendance.filter(status=TrainingAttendance.Status.PRESENT).count()
        return Response({
            "firing_records": FiringRecordSerializer(firing, many=True).data,
            "pt_records": PTRecordSerializer(pt, many=True).data,
            "attendance_percent": round(present / total_attendance * 100, 1) if total_attendance else None,
        })

    @action(detail=False, methods=["get"])
    def company(self, request):
        company_id = request.query_params.get("company")
        if not company_id:
            return Response({"detail": "company query param is required."}, status=status.HTTP_400_BAD_REQUEST)
        firing = FiringRecord.objects.filter(personnel__company_id=company_id)
        attendance = TrainingAttendance.objects.filter(personnel__company_id=company_id)
        return Response({
            "avg_firing_score": firing.aggregate(avg=Avg("score"))["avg"],
            "qualification_breakdown": list(
                firing.values("qualification").annotate(count=Count("id"))
            ),
            "attendance_present_count": attendance.filter(status=TrainingAttendance.Status.PRESENT).count(),
            "attendance_total_count": attendance.count(),
        })

    @action(detail=False, methods=["get"])
    def battalion(self, request):
        firing = FiringRecord.objects.all()
        pt = PTRecord.objects.all()
        return Response({
            "avg_firing_score": firing.aggregate(avg=Avg("score"))["avg"],
            "qualification_breakdown": list(firing.values("qualification").annotate(count=Count("id"))),
            "avg_pushups": pt.aggregate(avg=Avg("pushups"))["avg"],
            "avg_situps": pt.aggregate(avg=Avg("situps"))["avg"],
            "upcoming_battle_sessions": BattleTrainingSessionSerializer(
                BattleTrainingSession.objects.order_by("start_date")[:5], many=True
            ).data,
        })

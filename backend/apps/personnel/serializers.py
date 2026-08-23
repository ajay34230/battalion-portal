from rest_framework import serializers
from .models import Personnel, ServiceRecordEntry, Course, Award, Punishment, Transfer


class ServiceRecordEntrySerializer(serializers.ModelSerializer):
    class Meta:
        model = ServiceRecordEntry
        fields = ["id", "event_date", "description", "order_reference"]


class CourseSerializer(serializers.ModelSerializer):
    army_number = serializers.CharField(source="personnel.army_number", read_only=True)
    personnel_name = serializers.SerializerMethodField()

    class Meta:
        model = Course
        fields = ["id", "personnel", "army_number", "personnel_name", "course_name",
                  "institute", "start_date", "end_date", "grading"]

    def get_personnel_name(self, obj):
        return f"{obj.personnel.first_name} {obj.personnel.last_name}".strip()


class AwardSerializer(serializers.ModelSerializer):
    class Meta:
        model = Award
        fields = ["id", "title", "awarded_on", "citation"]


class PunishmentSerializer(serializers.ModelSerializer):
    class Meta:
        model = Punishment
        fields = ["id", "awarded_on", "details", "authority"]


class TransferSerializer(serializers.ModelSerializer):
    class Meta:
        model = Transfer
        fields = ["id", "personnel", "from_company", "to_company", "transfer_date", "order_reference"]


class PersonnelListSerializer(serializers.ModelSerializer):
    """Lightweight — used for list views and exports; avoids pulling every related set."""
    company_name = serializers.CharField(source="company.name", read_only=True)
    full_name = serializers.SerializerMethodField()

    class Meta:
        model = Personnel
        fields = ["id", "army_number", "rank", "full_name", "company_name", "trade",
                  "blood_group", "mobile_number", "is_active"]

    def get_full_name(self, obj):
        return f"{obj.first_name} {obj.last_name}".strip()


class PersonnelDetailSerializer(serializers.ModelSerializer):
    """Full profile — biodata, courses, leave history (reverse relation from apps.leave),
    awards, punishments, medical category, service record — matches the spec's
    'Personnel Profile' page in one payload."""
    service_records = ServiceRecordEntrySerializer(many=True, read_only=True)
    courses = CourseSerializer(many=True, read_only=True)
    awards = AwardSerializer(many=True, read_only=True)
    punishments = PunishmentSerializer(many=True, read_only=True)
    transfers = TransferSerializer(many=True, read_only=True)
    leave_applications = serializers.SerializerMethodField()

    class Meta:
        model = Personnel
        fields = [
            "id", "army_number", "rank", "first_name", "last_name", "father_name",
            "date_of_birth", "date_of_enrollment", "company", "platoon", "section",
            "appointment", "trade", "blood_group", "medical_category", "mobile_number",
            "emergency_contact_name", "emergency_contact_number", "address", "photograph",
            "is_active", "service_records", "courses", "awards", "punishments",
            "transfers", "leave_applications",
        ]

    def get_leave_applications(self, obj):
        from apps.leave.serializers import LeaveApplicationSerializer
        return LeaveApplicationSerializer(obj.leave_applications.all()[:20], many=True).data

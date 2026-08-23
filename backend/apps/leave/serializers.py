from rest_framework import serializers
from .models import LeaveApplication, LeaveApproval


class LeaveApprovalSerializer(serializers.ModelSerializer):
    actioned_by_name = serializers.CharField(source="actioned_by.get_full_name", read_only=True)

    class Meta:
        model = LeaveApproval
        fields = ["id", "stage", "actioned_by", "actioned_by_name", "action", "remarks", "actioned_at"]


class LeaveApplicationSerializer(serializers.ModelSerializer):
    days = serializers.ReadOnlyField()
    approval_trail = LeaveApprovalSerializer(many=True, read_only=True)

    class Meta:
        model = LeaveApplication
        fields = ["id", "personnel", "leave_type", "start_date", "end_date", "reason",
                  "status", "days", "applied_at", "approval_trail"]
        read_only_fields = ["status", "applied_at"]

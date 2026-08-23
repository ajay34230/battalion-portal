from django.utils import timezone
from rest_framework import serializers, viewsets
from rest_framework.decorators import action
from rest_framework.response import Response
from rest_framework.permissions import IsAuthenticated

from .models import Notice
from apps.accounts.permissions import CanApproveNotice


class NoticeSerializer(serializers.ModelSerializer):
    class Meta:
        model = Notice
        fields = ["id", "title", "category", "priority", "description", "attachment",
                  "status", "published_by", "published_at", "expiry_date", "created_at"]
        read_only_fields = ["status", "published_by", "published_at", "created_at"]


class NoticeViewSet(viewsets.ModelViewSet):
    """Notices auto-expire out of the active feed once expiry_date passes —
    handled by filtering here, not a background job, to keep the scaffold simple."""
    serializer_class = NoticeSerializer
    permission_classes = [IsAuthenticated]
    filterset_fields = ["status", "priority", "category"]

    def get_queryset(self):
        qs = Notice.objects.all()
        if self.request.query_params.get("active_only"):
            today = timezone.now().date()
            qs = qs.filter(status=Notice.Status.PUBLISHED).exclude(expiry_date__lt=today)
        return qs

    def get_permissions(self):
        if self.action in ("publish", "archive"):
            return [IsAuthenticated(), CanApproveNotice()]
        return [IsAuthenticated()]

    @action(detail=True, methods=["post"])
    def publish(self, request, pk=None):
        notice = self.get_object()
        notice.status = Notice.Status.PUBLISHED
        notice.published_by = request.user
        notice.published_at = timezone.now()
        notice.save(update_fields=["status", "published_by", "published_at"])
        return Response(NoticeSerializer(notice).data)

    @action(detail=True, methods=["post"])
    def archive(self, request, pk=None):
        notice = self.get_object()
        notice.status = Notice.Status.ARCHIVED
        notice.save(update_fields=["status"])
        return Response(NoticeSerializer(notice).data)

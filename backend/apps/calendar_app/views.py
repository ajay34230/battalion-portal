from datetime import timedelta, date as date_cls
from django.db.models import Q
from django.utils import timezone
from rest_framework import serializers, viewsets, status
from rest_framework.decorators import action
from rest_framework.response import Response
from rest_framework.permissions import IsAuthenticated

from .models import CalendarEvent
from apps.accounts.permissions import caps


class CalendarEventSerializer(serializers.ModelSerializer):
    class Meta:
        model = CalendarEvent
        fields = ["id", "title", "event_type", "scope", "company", "start_datetime",
                  "end_datetime", "location", "description", "reminder_minutes_before",
                  "created_by", "created_at"]
        read_only_fields = ["created_by", "created_at"]

    def validate(self, attrs):
        scope = attrs.get("scope", getattr(self.instance, "scope", None))
        company = attrs.get("company", getattr(self.instance, "company", None))
        if scope == CalendarEvent.Scope.COMPANY and not company:
            raise serializers.ValidationError({"company": "Required when scope is COMPANY."})
        if attrs.get("end_datetime") and attrs.get("start_datetime") and attrs["end_datetime"] < attrs["start_datetime"]:
            raise serializers.ValidationError({"end_datetime": "Must be on or after start_datetime."})
        return attrs


class CalendarEventViewSet(viewsets.ModelViewSet):
    """?view=daily|weekly|monthly&date=YYYY-MM-DD narrows the list to that
    window, as spec'd ('Calendar Views: Daily / Weekly / Monthly')."""
    serializer_class = CalendarEventSerializer
    permission_classes = [IsAuthenticated]
    filterset_fields = ["event_type", "scope", "company"]

    def get_queryset(self):
        qs = CalendarEvent.objects.select_related("company", "created_by")
        user = self.request.user
        # Scoping was previously keyed off "has a company_id", which wrongly
        # narrowed Company Commander (view_all=True but also company-assigned)
        # to only their own company's events — contradicting their view_all
        # capability. Scope by the actual capability instead.
        if not user.is_superuser and not caps(user).get("view_all") and user.company_id:
            qs = qs.filter(Q(scope=CalendarEvent.Scope.BATTALION) | Q(company_id=user.company_id))

        view = self.request.query_params.get("view")
        date_str = self.request.query_params.get("date")
        if view and date_str:
            try:
                anchor = date_cls.fromisoformat(date_str)
            except ValueError:
                return qs.none()  # invalid date -> empty result, not a 500
            if view == "daily":
                start, end = anchor, anchor + timedelta(days=1)
            elif view == "weekly":
                start = anchor - timedelta(days=anchor.weekday())
                end = start + timedelta(days=7)
            elif view == "monthly":
                start = anchor.replace(day=1)
                next_month = (start.replace(day=28) + timedelta(days=4)).replace(day=1)
                end = next_month
            else:
                return qs
            qs = qs.filter(start_datetime__date__gte=start, start_datetime__date__lt=end)
        return qs

    def get_permissions(self):
        # Any authenticated user can read; writing a BATTALION-scope event
        # needs view_all/edit_all, writing a COMPANY-scope event needs to
        # match that company (or edit_all). Enforced in perform_create/update
        # below since it depends on request-body content, not just the verb.
        return [IsAuthenticated()]

    def _check_write_allowed(self, scope, company_id):
        user = self.request.user
        if user.is_superuser or caps(user).get("edit_all"):
            return True
        if scope == CalendarEvent.Scope.BATTALION:
            return caps(user).get("view_all", False)
        return company_id is not None and user.company_id == company_id

    def perform_create(self, serializer):
        scope = serializer.validated_data.get("scope", CalendarEvent.Scope.BATTALION)
        company = serializer.validated_data.get("company")
        if not self._check_write_allowed(scope, company.id if company else None):
            from rest_framework.exceptions import PermissionDenied
            raise PermissionDenied("Not authorised to create an event with this scope.")
        serializer.save(created_by=self.request.user)

    def perform_update(self, serializer):
        instance = serializer.instance
        scope = serializer.validated_data.get("scope", instance.scope)
        company = serializer.validated_data.get("company", instance.company)
        if not self._check_write_allowed(scope, company.id if company else None):
            from rest_framework.exceptions import PermissionDenied
            raise PermissionDenied("Not authorised to modify an event with this scope.")
        serializer.save()

    @action(detail=False, methods=["get"])
    def upcoming_reminders(self, request):
        """Events with a reminder that falls within the next 24h — polled by
        the frontend rather than pushed, since there's no notification
        service wired up yet (see README limitations)."""
        now = timezone.now()
        window_events = []
        for event in self.get_queryset().filter(reminder_minutes_before__isnull=False, start_datetime__gte=now):
            reminder_time = event.start_datetime - timedelta(minutes=event.reminder_minutes_before)
            if now <= reminder_time <= now + timedelta(hours=24):
                window_events.append(event)
        return Response(CalendarEventSerializer(window_events, many=True).data)

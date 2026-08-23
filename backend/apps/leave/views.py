from rest_framework import viewsets, status
from rest_framework.decorators import action
from rest_framework.response import Response
from rest_framework.permissions import IsAuthenticated

from .models import LeaveApplication, LeaveApproval
from .serializers import LeaveApplicationSerializer
from apps.accounts.models import Role

# Defines which role must action each stage, and what the next stage is on approval.
# CLERK/COY_COMD/ADJUTANT are the new role names (previously COY_CLERK/COY_COMMANDER/ADJT).
WORKFLOW = {
    LeaveApplication.Status.PENDING_CLERK:   {"approver_role": Role.CLERK,           "next": LeaveApplication.Status.PENDING_COY_CDR},
    LeaveApplication.Status.PENDING_COY_CDR: {"approver_role": Role.COMPANY_COMMANDER, "next": LeaveApplication.Status.PENDING_ADJT},
    LeaveApplication.Status.PENDING_ADJT:    {"approver_role": Role.ADJUTANT,        "next": LeaveApplication.Status.APPROVED},
}


class LeaveApplicationViewSet(viewsets.ModelViewSet):
    queryset = LeaveApplication.objects.select_related("personnel", "personnel__company")
    serializer_class = LeaveApplicationSerializer
    permission_classes = [IsAuthenticated]
    filterset_fields = ["status", "leave_type", "personnel__company"]

    def get_queryset(self):
        qs = super().get_queryset()
        user = self.request.user
        # Company-scoped roles only ever see their own company's applications
        if not user.is_superuser and user.role in (Role.CLERK, Role.COMPANY_COMMANDER) and user.company_id:
            qs = qs.filter(personnel__company_id=user.company_id)
        return qs

    def perform_create(self, serializer):
        serializer.save(status=LeaveApplication.Status.PENDING_CLERK)

    @action(detail=True, methods=["post"])
    def approve(self, request, pk=None):
        return self._action_stage(request, pk, LeaveApproval.Action.APPROVED)

    @action(detail=True, methods=["post"])
    def reject(self, request, pk=None):
        return self._action_stage(request, pk, LeaveApproval.Action.REJECTED)

    def _action_stage(self, request, pk, action_type):
        application = self.get_object()
        stage_rule = WORKFLOW.get(application.status)

        if not stage_rule:
            return Response({"detail": f"No pending action for status '{application.status}'."},
                             status=status.HTTP_400_BAD_REQUEST)

        user = request.user
        is_correct_role = user.role == stage_rule["approver_role"]
        # Clerk/Company Commander stages are company-scoped: role alone isn't
        # enough — the approver must belong to the SAME company as the
        # applicant, or a Clerk/Commander from a different company could
        # approve another company's leave.
        if stage_rule["approver_role"] in (Role.CLERK, Role.COMPANY_COMMANDER):
            is_correct_role = is_correct_role and user.company_id == application.personnel.company_id

        if not is_correct_role and not user.is_superuser:
            return Response({"detail": "You are not authorised to action this stage."},
                             status=status.HTTP_403_FORBIDDEN)

        LeaveApproval.objects.create(
            application=application,
            stage=application.status,
            actioned_by=request.user,
            action=action_type,
            remarks=request.data.get("remarks", ""),
        )

        application.status = (
            LeaveApplication.Status.REJECTED if action_type == LeaveApproval.Action.REJECTED
            else stage_rule["next"]
        )
        application.save(update_fields=["status", "updated_at"])
        return Response(LeaveApplicationSerializer(application).data)

    @action(detail=True, methods=["post"])
    def cancel(self, request, pk=None):
        application = self.get_object()
        is_own_application = application.personnel_id == getattr(request.user, "linked_personnel_id", None)
        if not is_own_application and not request.user.is_superuser:
            return Response({"detail": "Only the applicant or an admin can cancel this application."},
                             status=status.HTTP_403_FORBIDDEN)
        application.status = LeaveApplication.Status.CANCELLED
        application.save(update_fields=["status", "updated_at"])
        return Response(LeaveApplicationSerializer(application).data)

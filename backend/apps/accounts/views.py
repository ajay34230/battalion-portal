from django.contrib.auth import get_user_model
from django.contrib.auth.tokens import default_token_generator
from django.utils.encoding import force_bytes
from django.utils.http import urlsafe_base64_encode, urlsafe_base64_decode
from django.core.mail import send_mail
from django.conf import settings
from rest_framework import viewsets, generics, status
from rest_framework.views import APIView
from rest_framework.response import Response
from rest_framework.permissions import IsAuthenticated, AllowAny
from rest_framework_simplejwt.views import TokenObtainPairView
from rest_framework_simplejwt.tokens import RefreshToken
from rest_framework_simplejwt.exceptions import TokenError

from .serializers import (
    BattalionTokenObtainPairSerializer, ChangePasswordSerializer,
    AdminResetPasswordSerializer, RequestPasswordResetSerializer,
    ConfirmPasswordResetSerializer, UserSerializer, UserCreateSerializer,
    PermissionMatrixSerializer,
)
from .permissions import IsAdjutantOrSuperuser

User = get_user_model()


class LoginView(TokenObtainPairView):
    """POST {username, password} -> {access, refresh, user}"""
    serializer_class = BattalionTokenObtainPairSerializer


class LogoutView(APIView):
    """POST {refresh} -> blacklists the refresh token so it can't be reused.
    Client must also discard the access token locally (it stays valid until
    it naturally expires — 30 min — since access tokens aren't tracked server-side)."""
    permission_classes = [IsAuthenticated]

    def post(self, request):
        try:
            token = RefreshToken(request.data.get("refresh", ""))
            token.blacklist()
        except TokenError:
            return Response({"detail": "Invalid or already-expired refresh token."},
                             status=status.HTTP_400_BAD_REQUEST)
        return Response({"detail": "Logged out."}, status=status.HTTP_200_OK)


class ChangePasswordView(APIView):
    """Self-service password change — requires the current password."""
    permission_classes = [IsAuthenticated]

    def post(self, request):
        serializer = ChangePasswordSerializer(data=request.data, context={"request": request})
        serializer.is_valid(raise_exception=True)
        serializer.save()
        return Response({"detail": "Password changed."})


class AdminResetPasswordView(APIView):
    """Adjutant/superuser resets another user's password (e.g. lost credentials).
    Forces must_change_password=True so the user is prompted to set their own on next login."""
    permission_classes = [IsAuthenticated, IsAdjutantOrSuperuser]

    def post(self, request, user_id):
        target_user = generics.get_object_or_404(User, id=user_id)
        serializer = AdminResetPasswordSerializer(data=request.data)
        serializer.is_valid(raise_exception=True)
        serializer.save(target_user)
        return Response({"detail": f"Password reset for {target_user.username}."})


class RequestPasswordResetView(APIView):
    """Self-service 'forgot password', step 1: POST {username} -> issues a
    single-use uid/token pair. Always returns 200 regardless of whether the
    username exists, to avoid leaking which usernames are valid.

    Delivery: if the user has an email on file and EMAIL_HOST is configured
    (see config/settings/production.py), the reset link is emailed. On this
    LAN deployment SMTP may not be configured — in that case the token is
    only written to the server log for the Adjutant to relay in person. This
    is a real limitation of a fully offline LAN: there's no messaging channel
    to a locked-out user that doesn't itself require being logged in."""
    permission_classes = [AllowAny]

    def post(self, request):
        serializer = RequestPasswordResetSerializer(data=request.data)
        serializer.is_valid(raise_exception=True)
        username = serializer.validated_data["username"]

        try:
            user = User.objects.get(username=username)
        except User.DoesNotExist:
            return Response({"detail": "If that account exists, a reset link has been issued."})

        uid = urlsafe_base64_encode(force_bytes(user.pk))
        token = default_token_generator.make_token(user)

        if user.email and getattr(settings, "EMAIL_HOST", None):
            send_mail(
                subject="15 Rajputana Rifles Battalion Portal — Password Reset",
                message=f"Reset your password using uid={uid} token={token}. "
                        f"This link is valid for a limited time and can only be used once.",
                from_email=getattr(settings, "DEFAULT_FROM_EMAIL", "no-reply@battalion-portal.local"),
                recipient_list=[user.email],
                fail_silently=True,
            )
        else:
            import logging
            logging.getLogger("django").warning(
                f"Password reset requested for '{username}' but no email/SMTP configured. "
                f"uid={uid} token={token} — relay this to the user through a verified channel."
            )

        return Response({"detail": "If that account exists, a reset link has been issued."})


class ConfirmPasswordResetView(APIView):
    """Self-service 'forgot password', step 2: POST {uid, token, new_password}."""
    permission_classes = [AllowAny]

    def post(self, request):
        serializer = ConfirmPasswordResetSerializer(data=request.data)
        serializer.is_valid(raise_exception=True)

        try:
            uid = urlsafe_base64_decode(serializer.validated_data["uid"]).decode()
            user = User.objects.get(pk=uid)
        except (User.DoesNotExist, ValueError, TypeError, OverflowError):
            return Response({"detail": "Invalid reset link."}, status=status.HTTP_400_BAD_REQUEST)

        if not default_token_generator.check_token(user, serializer.validated_data["token"]):
            return Response({"detail": "Invalid or expired reset link."}, status=status.HTTP_400_BAD_REQUEST)

        user.set_password(serializer.validated_data["new_password"])
        user.must_change_password = False
        user.save(update_fields=["password", "must_change_password"])
        return Response({"detail": "Password has been reset. You can now log in."})


class UserViewSet(viewsets.ModelViewSet):
    """Full user CRUD — create user / assign role / disable user, restricted
    to Adjutant or superuser. 'Disable' is done via is_active=False (PATCH),
    never a hard delete, to preserve the audit trail on their historical actions."""
    queryset = User.objects.select_related("company").order_by("rank", "first_name")
    permission_classes = [IsAuthenticated, IsAdjutantOrSuperuser]
    filterset_fields = ["role", "company", "is_active"]

    def get_serializer_class(self):
        return UserCreateSerializer if self.action == "create" else UserSerializer


class PermissionMatrixView(APIView):
    permission_classes = [IsAuthenticated]

    def get(self, request):
        return Response(PermissionMatrixSerializer(instance={}).data)

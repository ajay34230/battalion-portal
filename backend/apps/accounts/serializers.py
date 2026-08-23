from django.contrib.auth import get_user_model
from django.contrib.auth.password_validation import validate_password
from rest_framework import serializers
from rest_framework_simplejwt.serializers import TokenObtainPairSerializer

from .models import ROLE_CAPABILITIES, Role
from .permissions import caps

User = get_user_model()


class BattalionTokenObtainPairSerializer(TokenObtainPairSerializer):
    """Adds role/company/must_change_password to the login response so the
    frontend can route/render without a second API call."""

    def validate(self, attrs):
        data = super().validate(attrs)
        data["user"] = {
            "id": self.user.id,
            "username": self.user.username,
            "full_name": self.user.get_full_name(),
            "rank": self.user.rank,
            "role": self.user.role,
            "role_display": self.user.get_role_display(),
            "company_id": self.user.company_id,
            "is_superuser": self.user.is_superuser,
            "must_change_password": self.user.must_change_password,
            "capabilities": caps(self.user),
        }
        return data


class ChangePasswordSerializer(serializers.Serializer):
    old_password = serializers.CharField(write_only=True)
    new_password = serializers.CharField(write_only=True)

    def validate_old_password(self, value):
        user = self.context["request"].user
        if not user.check_password(value):
            raise serializers.ValidationError("Current password is incorrect.")
        return value

    def validate_new_password(self, value):
        validate_password(value, self.context["request"].user)
        return value

    def save(self):
        user = self.context["request"].user
        user.set_password(self.validated_data["new_password"])
        user.must_change_password = False
        user.save(update_fields=["password", "must_change_password"])
        return user


class AdminResetPasswordSerializer(serializers.Serializer):
    """Used by the Adjutant/superuser to reset another user's password —
    distinct from ChangePasswordSerializer, which requires the old password."""
    new_password = serializers.CharField(write_only=True)

    def validate_new_password(self, value):
        validate_password(value)
        return value

    def save(self, target_user):
        target_user.set_password(self.validated_data["new_password"])
        target_user.must_change_password = True
        target_user.save(update_fields=["password", "must_change_password"])
        return target_user


class RequestPasswordResetSerializer(serializers.Serializer):
    """Self-service 'forgot password' — step 1. Always returns success even if
    the username doesn't exist, so this endpoint can't be used to enumerate
    valid usernames on the battalion LAN."""
    username = serializers.CharField()


class ConfirmPasswordResetSerializer(serializers.Serializer):
    """Self-service 'forgot password' — step 2, using the uid/token issued
    by RequestPasswordResetSerializer (delivered out-of-band, e.g. by the
    Adjutant reading it off the server admin console, since this LAN
    deployment has no guaranteed SMTP relay — see AdminResetPasswordView
    for the in-person alternative)."""
    uid = serializers.CharField()
    token = serializers.CharField()
    new_password = serializers.CharField(write_only=True)

    def validate_new_password(self, value):
        validate_password(value)
        return value


class UserSerializer(serializers.ModelSerializer):
    role_display = serializers.CharField(source="get_role_display", read_only=True)

    class Meta:
        model = User
        fields = [
            "id", "username", "first_name", "last_name", "rank", "role", "role_display",
            "company", "linked_personnel", "service_number", "mobile_number", "is_active",
            "is_active_duty", "is_superuser", "must_change_password", "last_login", "date_joined",
        ]
        read_only_fields = ["last_login", "date_joined"]


class UserCreateSerializer(serializers.ModelSerializer):
    password = serializers.CharField(write_only=True)

    class Meta:
        model = User
        fields = [
            "id", "username", "password", "first_name", "last_name", "rank", "role",
            "company", "linked_personnel", "service_number", "mobile_number",
        ]

    def validate_password(self, value):
        validate_password(value)
        return value

    def validate(self, attrs):
        role = attrs.get("role")
        company = attrs.get("company")
        if role in (Role.COMPANY_COMMANDER, Role.CLERK) and not company:
            raise serializers.ValidationError({"company": "Required for this role."})
        return attrs

    def create(self, validated_data):
        password = validated_data.pop("password")
        user = User(**validated_data, must_change_password=True)
        user.set_password(password)
        user.save()
        return user


class PermissionMatrixSerializer(serializers.Serializer):
    """Read-only view of the full role -> capability matrix, for the
    User Management screen's permission matrix table."""
    def to_representation(self, instance):
        return {
            role.value: {"label": role.label, **ROLE_CAPABILITIES.get(role, {})}
            for role in Role
        }

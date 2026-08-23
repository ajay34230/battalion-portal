from django.contrib.auth.models import AbstractUser
from django.db import models


class Role(models.TextChoices):
    """Full access ('everything') is modeled with Django's built-in is_superuser
    flag rather than a role value, so it isn't a role a clerk could be
    reassigned into by accident. These 10 are the operational roles."""
    BATTALION_COMMANDER = "BN_COMD", "Battalion Commander"
    SECOND_IN_COMMAND = "IC_2", "Second-in-Command"
    ADJUTANT = "ADJUTANT", "Adjutant"
    COMPANY_COMMANDER = "COY_COMD", "Company Commander"
    JCO = "JCO", "JCO"
    CLERK = "CLERK", "Clerk"
    STORE_INCHARGE = "STORE_IC", "Store Incharge"
    VEHICLE_INCHARGE = "VEH_IC", "Vehicle Incharge"
    TRAINING_NCO = "TRG_NCO", "Training NCO"
    READ_ONLY = "READ_ONLY", "Read Only User"


class User(AbstractUser):
    """Battalion portal user. Company-scoped roles (Company Commander, Clerk)
    must have `company` set — enforced in clean(), not just convention."""
    role = models.CharField(max_length=20, choices=Role.choices, default=Role.READ_ONLY)
    company = models.ForeignKey(
        "companies.Company", null=True, blank=True, on_delete=models.SET_NULL,
        related_name="users", help_text="Required for Company Commander / Clerk roles"
    )
    # Explicit link from a login account to its personnel record, so
    # self-service actions (e.g. "cancel my own leave application") have a
    # real FK to check instead of comparing army_number to service_number
    # as strings and hoping they were entered identically.
    linked_personnel = models.OneToOneField(
        "personnel.Personnel", null=True, blank=True, on_delete=models.SET_NULL,
        related_name="user_account", help_text="The personnel record this login belongs to, if any"
    )
    rank = models.CharField(max_length=40, blank=True)
    service_number = models.CharField(max_length=20, unique=True, null=True, blank=True)
    mobile_number = models.CharField(max_length=15, blank=True)
    is_active_duty = models.BooleanField(default=True)
    last_login_ip = models.GenericIPAddressField(null=True, blank=True)
    must_change_password = models.BooleanField(
        default=True, help_text="Forces a password change on next login for newly created accounts"
    )

    class Meta:
        db_table = "accounts_user"

    def __str__(self):
        return f"{self.rank} {self.get_full_name() or self.username} ({self.get_role_display()})"

    def clean(self):
        from django.core.exceptions import ValidationError
        if self.role in (Role.COMPANY_COMMANDER, Role.CLERK) and not self.company_id and not self.is_superuser:
            raise ValidationError({"company": "This role requires a company to be assigned."})


# ---- Role capability matrix — data-driven so it's auditable and easy to amend. ----
# view_all: can read across the whole battalion, not just own company/module
# edit_own_company: can create/edit records only within `request.user.company`
# module: single-purpose roles are restricted to this module regardless of view_all
ROLE_CAPABILITIES = {
    Role.BATTALION_COMMANDER: {"view_all": True, "edit_all": False, "approve_leave": True,  "approve_notice": True,  "manage_users": False},
    Role.SECOND_IN_COMMAND:   {"view_all": True, "edit_all": False, "approve_leave": True,  "approve_notice": False, "manage_users": False},
    Role.ADJUTANT:            {"view_all": True, "edit_all": True,  "approve_leave": True,  "approve_notice": True,  "manage_users": True},
    Role.COMPANY_COMMANDER:   {"view_all": True, "edit_all": False, "approve_leave": True,  "approve_notice": False, "manage_users": False, "edit_own_company": True},
    Role.JCO:                 {"view_all": False, "edit_all": False, "approve_leave": False, "approve_notice": False, "manage_users": False, "edit_own_company": True},
    Role.CLERK:               {"view_all": False, "edit_all": False, "approve_leave": False, "approve_notice": False, "manage_users": False, "edit_own_company": True},
    Role.STORE_INCHARGE:      {"view_all": False, "edit_all": False, "approve_leave": False, "approve_notice": False, "manage_users": False, "module": "inventory"},
    Role.VEHICLE_INCHARGE:    {"view_all": False, "edit_all": False, "approve_leave": False, "approve_notice": False, "manage_users": False, "module": "vehicles"},
    Role.TRAINING_NCO:        {"view_all": False, "edit_all": False, "approve_leave": False, "approve_notice": False, "manage_users": False, "module": "training"},
    Role.READ_ONLY:           {"view_all": True, "edit_all": False, "approve_leave": False, "approve_notice": False, "manage_users": False, "read_only": True},
}

# Superusers (is_superuser=True) bypass this matrix entirely at the permission
# layer — see apps/accounts/permissions.py `caps()`.

# Leave approval chain, referenced by apps/leave/views.py
LEAVE_APPROVAL_CHAIN = [Role.CLERK, Role.COMPANY_COMMANDER, Role.ADJUTANT]

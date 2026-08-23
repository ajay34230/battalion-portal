from rest_framework import permissions
from .models import ROLE_CAPABILITIES


def caps(user):
    """Superusers get full capabilities without needing an entry in the matrix."""
    if user.is_superuser:
        return {"view_all": True, "edit_all": True, "approve_leave": True,
                "approve_notice": True, "manage_users": True}
    return ROLE_CAPABILITIES.get(user.role, {})


def _resolve_company_id(obj):
    """Objects may carry company directly (Personnel, Company) or via a
    `personnel` FK (Course, and other personnel-linked records). Checks both
    so ReadOnlyOrHasEditRights works across either shape without each app
    needing its own permission subclass."""
    company = getattr(obj, "company", None)
    if company is not None:
        return company.id
    personnel = getattr(obj, "personnel", None)
    if personnel is not None and personnel.company_id:
        return personnel.company_id
    return None


def _obj_company_matches(obj, user):
    company_id = _resolve_company_id(obj)
    return company_id is not None and user.company_id == company_id


class IsAdjutantOrSuperuser(permissions.BasePermission):
    """User management (create user, assign role, reset password, disable user)
    is restricted to the Adjutant role or a superuser."""
    def has_permission(self, request, view):
        return request.user.is_authenticated and caps(request.user).get("manage_users", False)


class ReadOnlyOrHasEditRights(permissions.BasePermission):
    """Generic object-level rule used across Personnel/Companies/Weapons/Vehicles/Store.
    Safe methods require only authentication + view scope. Write methods require
    edit_all, or edit_own_company scoped to the object's company."""

    def has_permission(self, request, view):
        if not request.user.is_authenticated:
            return False
        if request.method == "POST":
            # There's no object yet for has_object_permission to scope against,
            # so creation rights must be checked here — previously this method
            # only checked is_authenticated, meaning ANY logged-in user (Read
            # Only, Store Incharge, etc.) could create Personnel/Company records.
            c = caps(request.user)
            if c.get("edit_all"):
                return True
            if c.get("edit_own_company"):
                # The payload may carry `company` directly (Personnel, Company)
                # or `personnel` (Course, and similar personnel-linked models) —
                # resolve either to a company id and compare to the user's own.
                target_company = request.data.get("company")
                if target_company is not None:
                    return str(request.user.company_id) == str(target_company)
                personnel_id = request.data.get("personnel")
                if personnel_id is not None:
                    from apps.personnel.models import Personnel
                    return Personnel.objects.filter(
                        id=personnel_id, company_id=request.user.company_id
                    ).exists()
                return False
            return False
        return True

    def has_object_permission(self, request, view, obj):
        user = request.user
        c = caps(user)

        if request.method in permissions.SAFE_METHODS:
            if c.get("view_all"):
                return True
            return _obj_company_matches(obj, user)

        if c.get("edit_all"):
            return True
        if c.get("edit_own_company"):
            return _obj_company_matches(obj, user)
        return False


class CanApproveLeave(permissions.BasePermission):
    def has_permission(self, request, view):
        return request.user.is_authenticated and caps(request.user).get("approve_leave", False)


class CanApproveNotice(permissions.BasePermission):
    def has_permission(self, request, view):
        return request.user.is_authenticated and caps(request.user).get("approve_notice", False)


class ModuleScopedPermission(permissions.BasePermission):
    """For single-purpose roles (Training NCO, Vehicle Incharge, Store Incharge)
    that should only touch their own module. view_all roles can still read;
    only edit_all or the matching module grants write access."""
    required_module = None

    def has_permission(self, request, view):
        user = request.user
        if not user.is_authenticated:
            return False
        c = caps(user)
        if c.get("edit_all"):
            return True
        if request.method in permissions.SAFE_METHODS:
            return c.get("view_all", False) or c.get("module") == self.required_module
        return c.get("module") == self.required_module

    @classmethod
    def for_module(cls, module_name):
        return type(f"ModuleScoped_{module_name}", (cls,), {"required_module": module_name})

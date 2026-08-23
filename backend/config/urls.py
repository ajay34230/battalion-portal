from django.contrib import admin
from django.urls import path, include
from django.conf import settings
from django.conf.urls.static import static
from rest_framework.routers import DefaultRouter

from apps.accounts.views import (
    LoginView, LogoutView, ChangePasswordView, AdminResetPasswordView,
    RequestPasswordResetView, ConfirmPasswordResetView, UserViewSet, PermissionMatrixView,
)
from rest_framework_simplejwt.views import TokenRefreshView
from apps.personnel.views import PersonnelViewSet, CourseViewSet
from apps.companies.views import CompanyViewSet, PlatoonViewSet, SectionViewSet
from apps.leave.views import LeaveApplicationViewSet
from apps.weapons.views import WeaponViewSet
from apps.vehicles.views import VehicleViewSet, FuelRecordViewSet, ServiceRecordViewSet
from apps.inventory.views import InventoryItemViewSet, StockMovementViewSet, VendorViewSet
from apps.notices.views import NoticeViewSet
from apps.documents.views import FolderViewSet, DocumentViewSet
from apps.dashboard.views import DashboardView
from apps.training.views import (
    FiringRecordViewSet, PTRecordViewSet, BattleTrainingSessionViewSet,
    CompetitionViewSet, TrainingAttendanceViewSet, TrainingReportView,
)
from apps.directory.views import DirectoryViewSet
from apps.calendar_app.views import CalendarEventViewSet
from apps.messaging.views import ConversationViewSet
from apps.maps.views import MapFileViewSet
from apps.operations.views import OperationalRecordViewSet
from apps.mis.views import MISDashboardView

router = DefaultRouter()
router.register(r"users", UserViewSet, basename="user")
router.register(r"personnel", PersonnelViewSet, basename="personnel")
router.register(r"courses", CourseViewSet, basename="course")
router.register(r"companies", CompanyViewSet, basename="company")
router.register(r"platoons", PlatoonViewSet, basename="platoon")
router.register(r"sections", SectionViewSet, basename="section")
router.register(r"leave-applications", LeaveApplicationViewSet, basename="leave-application")
router.register(r"weapons", WeaponViewSet, basename="weapon")
router.register(r"vehicles", VehicleViewSet, basename="vehicle")
router.register(r"fuel-records", FuelRecordViewSet, basename="fuel-record")
router.register(r"vehicle-service-records", ServiceRecordViewSet, basename="vehicle-service-record")
router.register(r"inventory-items", InventoryItemViewSet, basename="inventory-item")
router.register(r"stock-movements", StockMovementViewSet, basename="stock-movement")
router.register(r"vendors", VendorViewSet, basename="vendor")
router.register(r"notices", NoticeViewSet, basename="notice")
router.register(r"folders", FolderViewSet, basename="folder")
router.register(r"documents", DocumentViewSet, basename="document")
router.register(r"training/firing-records", FiringRecordViewSet, basename="firing-record")
router.register(r"training/pt-records", PTRecordViewSet, basename="pt-record")
router.register(r"training/battle-sessions", BattleTrainingSessionViewSet, basename="battle-session")
router.register(r"training/competitions", CompetitionViewSet, basename="competition")
router.register(r"training/attendance", TrainingAttendanceViewSet, basename="training-attendance")
router.register(r"training/reports", TrainingReportView, basename="training-report")
router.register(r"directory", DirectoryViewSet, basename="directory")
router.register(r"calendar-events", CalendarEventViewSet, basename="calendar-event")
router.register(r"conversations", ConversationViewSet, basename="conversation")
router.register(r"maps", MapFileViewSet, basename="map")
router.register(r"operations", OperationalRecordViewSet, basename="operational-record")
router.register(r"mis", MISDashboardView, basename="mis")

urlpatterns = [
    path("admin/", admin.site.urls),

    # Auth
    path("api/auth/login/", LoginView.as_view(), name="login"),
    path("api/auth/logout/", LogoutView.as_view(), name="logout"),
    path("api/auth/token/refresh/", TokenRefreshView.as_view(), name="token_refresh"),
    path("api/auth/change-password/", ChangePasswordView.as_view(), name="change_password"),
    path("api/auth/password-reset/request/", RequestPasswordResetView.as_view(), name="password_reset_request"),
    path("api/auth/password-reset/confirm/", ConfirmPasswordResetView.as_view(), name="password_reset_confirm"),
    path("api/auth/users/<int:user_id>/reset-password/", AdminResetPasswordView.as_view(), name="admin_reset_password"),
    path("api/auth/permission-matrix/", PermissionMatrixView.as_view(), name="permission_matrix"),

    # Dashboard
    path("api/dashboard/", DashboardView.as_view(), name="dashboard"),

    # Everything else
    path("api/", include(router.urls)),
]

if settings.DEBUG:
    urlpatterns += static(settings.MEDIA_URL, document_root=settings.MEDIA_ROOT)

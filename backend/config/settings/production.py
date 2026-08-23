from decouple import config
from .base import *  # noqa

DEBUG = False

# --- Security requirements from the spec ---
SECURE_SSL_REDIRECT = config("SECURE_SSL_REDIRECT", default=True, cast=bool)
SESSION_COOKIE_SECURE = True
CSRF_COOKIE_SECURE = True
SECURE_HSTS_SECONDS = 31536000
SECURE_HSTS_INCLUDE_SUBDOMAINS = True
SECURE_CONTENT_TYPE_NOSNIFF = True
X_FRAME_OPTIONS = "DENY"

# Behind nginx terminating TLS — trust its forwarded-proto header
SECURE_PROXY_SSL_HEADER = ("HTTP_X_FORWARDED_PROTO", "https")

# Optional SMTP relay for password-reset emails. If unset, RequestPasswordResetView
# falls back to logging the reset token for the Adjutant to relay in person —
# see apps/accounts/views.py for why (no guaranteed outbound path on a closed LAN).
EMAIL_HOST = config("EMAIL_HOST", default=None)
EMAIL_PORT = config("EMAIL_PORT", default=587, cast=int)
EMAIL_HOST_USER = config("EMAIL_HOST_USER", default="")
EMAIL_HOST_PASSWORD = config("EMAIL_HOST_PASSWORD", default="")
EMAIL_USE_TLS = config("EMAIL_USE_TLS", default=True, cast=bool)
DEFAULT_FROM_EMAIL = config("DEFAULT_FROM_EMAIL", default="no-reply@battalion-portal.local")

# IP restriction: enforce your unit LAN subnet at the nginx/firewall layer
# (see deploy/nginx.conf `allow`/`deny` block) rather than in Django — it's
# cheaper and fails closed before requests even reach the app server.

from django.db import models
from django.conf import settings
from auditlog.registry import auditlog


class MapFile(models.Model):
    """A map is a file (image/PDF) with optional geo-tag bounds so the frontend
    can overlay it, plus a flag marking it available for offline caching by the
    mobile client (the actual offline-cache mechanism is a frontend concern —
    this just tracks which maps are approved for it)."""
    title = models.CharField(max_length=150)
    file = models.FileField(upload_to="maps/%Y/%m/")
    description = models.TextField(blank=True)
    # Simple bounding box for geo-tagging; a full tile/projection system is
    # out of scope for this module — this is enough to pin a map on a
    # basic lat/lng-bounded overlay.
    north_lat = models.DecimalField(max_digits=9, decimal_places=6, null=True, blank=True)
    south_lat = models.DecimalField(max_digits=9, decimal_places=6, null=True, blank=True)
    east_lng = models.DecimalField(max_digits=9, decimal_places=6, null=True, blank=True)
    west_lng = models.DecimalField(max_digits=9, decimal_places=6, null=True, blank=True)
    available_offline = models.BooleanField(default=False)
    uploaded_by = models.ForeignKey(settings.AUTH_USER_MODEL, null=True, on_delete=models.SET_NULL)
    uploaded_at = models.DateTimeField(auto_now_add=True)

    class Meta:
        db_table = "maps_mapfile"
        ordering = ["-uploaded_at"]

    def __str__(self):
        return self.title


auditlog.register(MapFile)

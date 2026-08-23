from django.db import models
from django.conf import settings
from auditlog.registry import auditlog


class Folder(models.Model):
    """Nextcloud-style folder tree. Root folders (SOPs, Standing Orders, Circulars,
    Training Documents, Administrative Documents) have parent=None."""
    name = models.CharField(max_length=150)
    parent = models.ForeignKey("self", null=True, blank=True, on_delete=models.CASCADE, related_name="subfolders")
    # Roles allowed to view this folder's contents; empty = everyone with portal access.
    # Kept as a simple CSV of Role values rather than a M2M table — good enough for a
    # handful of folders; move to a join table if the folder count grows large.
    restricted_to_roles = models.CharField(max_length=255, blank=True,
                                            help_text="Comma-separated Role values; blank = unrestricted")

    class Meta:
        db_table = "documents_folder"
        unique_together = ["name", "parent"]

    def __str__(self):
        return self.name

    def path(self):
        parts, node = [self.name], self.parent
        while node:
            parts.append(node.name)
            node = node.parent
        return " / ".join(reversed(parts))


class Document(models.Model):
    folder = models.ForeignKey(Folder, on_delete=models.CASCADE, related_name="documents")
    name = models.CharField(max_length=200)
    file = models.FileField(upload_to="documents/%Y/%m/")
    uploaded_by = models.ForeignKey(settings.AUTH_USER_MODEL, null=True, on_delete=models.SET_NULL)
    uploaded_at = models.DateTimeField(auto_now_add=True)
    is_archived = models.BooleanField(default=False)

    class Meta:
        db_table = "documents_document"
        ordering = ["-uploaded_at"]

    def __str__(self):
        return self.name


class DocumentVersion(models.Model):
    """A new upload to an existing Document name creates a version row rather
    than overwriting the file, so history is always recoverable."""
    document = models.ForeignKey(Document, on_delete=models.CASCADE, related_name="versions")
    file = models.FileField(upload_to="documents/versions/%Y/%m/")
    uploaded_by = models.ForeignKey(settings.AUTH_USER_MODEL, null=True, on_delete=models.SET_NULL)
    uploaded_at = models.DateTimeField(auto_now_add=True)
    note = models.CharField(max_length=200, blank=True)

    class Meta:
        db_table = "documents_version"
        ordering = ["-uploaded_at"]


auditlog.register(Folder)
auditlog.register(Document)
auditlog.register(DocumentVersion)

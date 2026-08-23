from django.contrib import admin
from .models import Folder, Document, DocumentVersion
@admin.register(Folder)
class FolderAdmin(admin.ModelAdmin):
    list_display = ["name", "parent", "restricted_to_roles"]
@admin.register(Document)
class DocumentAdmin(admin.ModelAdmin):
    list_display = ["name", "folder", "uploaded_by", "uploaded_at", "is_archived"]
    list_filter = ["folder", "is_archived"]
@admin.register(DocumentVersion)
class DocumentVersionAdmin(admin.ModelAdmin):
    list_display = ["document", "uploaded_by", "uploaded_at"]
